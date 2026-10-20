package com.petmate.appointment;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import com.petmate.service.ServiceCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 用户端服务预约（v13 行业化升级）。
 *
 * 下单校验链（对齐市面平台的真实约束）：
 *   服务在售 → 规格档位属于该服务 → 加购项属于该服务 → 宠物属于本人
 *   → 时间落在营业时段切片上 → 该时段余位 > 0 → 上门服务必须有地址
 *   → 计算金额（规格价 × 天数 + 加购 + 上门费）→ 生成到店核销码
 */
@RestController
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SQL_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public AppointmentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ========================================================================
    // 创建预约
    // ========================================================================

    @PostMapping("/appointment")
    @Transactional
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();

        // ---- 1. 服务 ----
        long serviceId = requireLong(body, "serviceId", "请选择服务项");
        List<Map<String, Object>> svcRows = jdbc.queryForList(
            "SELECT si.id, si.name, si.category, si.sub_category, si.store_id, si.status, si.service_mode, " +
            "       si.onsite_fee, si.duration, si.price, si.open_time, si.close_time, si.slot_minutes, " +
            "       si.slot_capacity, si.advance_days, s.name AS store_name, s.address AS store_address " +
            "FROM t_service_item si JOIN t_store s ON si.store_id = s.id " +
            "WHERE si.id = ? AND si.is_deleted = 0 FOR UPDATE", serviceId);
        if (svcRows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "服务项不存在");
        Map<String, Object> svc = svcRows.get(0);
        if (((Number) svc.get("status")).intValue() != 1)
            throw new BizException(ErrorCode.PARAM_ERROR, "该服务已下架，暂不可预约");

        String category = (String) svc.get("category");
        String serviceMode = String.valueOf(svc.get("service_mode"));
        boolean dailyPriced = ServiceCatalog.isDailyPriced(category);

        // ---- 2. 规格档位 ----
        List<Map<String, Object>> specs = jdbc.queryForList(
            "SELECT id, name, price, duration FROM t_service_spec " +
            "WHERE service_id = ? AND is_deleted = 0 ORDER BY sort_no, price", serviceId);

        Long specId = optLong(body, "specId");
        Map<String, Object> spec = null;
        if (!specs.isEmpty()) {
            if (specId == null) throw new BizException(ErrorCode.PARAM_ERROR, "请选择服务规格（体型 / 套餐档位）");
            for (Map<String, Object> s : specs) {
                if (((Number) s.get("id")).longValue() == specId) { spec = s; break; }
            }
            if (spec == null) throw new BizException(ErrorCode.PARAM_ERROR, "所选规格不属于该服务");
        }

        BigDecimal unitPrice = spec != null
            ? new BigDecimal(spec.get("price").toString())
            : new BigDecimal(svc.get("price").toString());
        int specDuration = spec != null
            ? ((Number) spec.get("duration")).intValue()
            : ((Number) svc.get("duration")).intValue();
        String specName = spec != null ? (String) spec.get("name") : null;

        // ---- 3. 天数（仅寄养类按天计价）----
        int days = 1;
        if (dailyPriced) {
            Object d = body.get("days");
            days = d == null ? 1 : ((Number) d).intValue();
            if (days < 1 || days > 90) throw new BizException(ErrorCode.PARAM_ERROR, "寄养天数需在 1-90 天之间");
        }

        // ---- 4. 加购项 ----
        List<Long> addonIds = parseIdList(body.get("addonIds"));
        BigDecimal addonAmount = BigDecimal.ZERO;
        int addonDuration = 0;
        List<String> addonNames = new ArrayList<>();
        if (!addonIds.isEmpty()) {
            String in = addonIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
            List<Map<String, Object>> addons = jdbc.queryForList(
                "SELECT id, name, price, duration FROM t_service_addon " +
                "WHERE id IN (" + in + ") AND service_id = ? AND is_deleted = 0", serviceId);
            if (addons.size() != addonIds.size())
                throw new BizException(ErrorCode.PARAM_ERROR, "部分加购项不可用，请刷新后重试");
            for (Map<String, Object> a : addons) {
                addonAmount = addonAmount.add(new BigDecimal(a.get("price").toString()));
                addonDuration += ((Number) a.get("duration")).intValue();
                addonNames.add(String.valueOf(a.get("name")));
            }
        }

        // ---- 5. 宠物（必须是本人的宠物，体型决定档位）----
        long petId = requireLong(body, "petId", "请选择本次服务的宠物");
        List<Map<String, Object>> petRows = jdbc.queryForList(
            "SELECT id, name FROM t_pet WHERE id = ? AND user_id = ? AND is_deleted = 0", petId, uid);
        if (petRows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "宠物档案不存在或不属于当前账号");
        String petName = (String) petRows.get(0).get("name");

        // ---- 6. 预约时间：必须落在营业时段切片上 ----
        String rawTime = str(body.get("appointmentTime"));
        if (rawTime == null || rawTime.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择预约时间");
        LocalDateTime start = parseDateTime(rawTime);
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now.plusMinutes(29)))
            throw new BizException(ErrorCode.PARAM_ERROR, "请至少提前 30 分钟预约");

        int advanceDays = ((Number) svc.get("advance_days")).intValue();
        if (start.toLocalDate().isAfter(LocalDate.now().plusDays(advanceDays)))
            throw new BizException(ErrorCode.PARAM_ERROR, "最多可提前 " + advanceDays + " 天预约");

        LocalTime open = parseTime(str(svc.get("open_time")), LocalTime.of(9, 0));
        LocalTime close = parseTime(str(svc.get("close_time")), LocalTime.of(20, 0));
        int slotMinutes = Math.max(15, ((Number) svc.get("slot_minutes")).intValue());
        int capacity = Math.max(1, ((Number) svc.get("slot_capacity")).intValue());

        LocalTime t = start.toLocalTime().withSecond(0).withNano(0);
        if (t.isBefore(open) || t.plusMinutes(slotMinutes).isAfter(close))
            throw new BizException(ErrorCode.PARAM_ERROR,
                "该服务可预约时间为 " + open.format(HHMM) + "-" + close.format(HHMM) + "，请重新选择");
        long offset = java.time.Duration.between(open, t).toMinutes();
        if (offset % slotMinutes != 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择系统提供的可约时段");

        // ---- 7. 时段余位（同一事务内加锁校验，避免并发撞单）----
        Integer used = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_appointment WHERE service_id = ? AND appointment_time = ? " +
            "AND is_deleted = 0 AND status IN ('pending','confirmed')",
            Integer.class, serviceId, start.format(SQL_DT));
        if (used != null && used >= capacity)
            throw new BizException(ErrorCode.PARAM_ERROR, "该时段已约满，请换个时间段");

        // 同一用户同一服务同一时段不可重复下单
        Integer dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_appointment WHERE user_id = ? AND service_id = ? AND appointment_time = ? " +
            "AND is_deleted = 0 AND status IN ('pending','confirmed')",
            Integer.class, uid, serviceId, start.format(SQL_DT));
        if (dup != null && dup > 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "你已预约该时段，请勿重复提交");

        // ---- 8. 联系方式 / 上门地址 ----
        String contactName = trimTo(str(body.get("contactName")), 64);
        String contactPhone = trimTo(str(body.get("contactPhone")), 20);
        if (contactPhone == null || contactPhone.isBlank()) {
            contactPhone = jdbc.query("SELECT phone FROM t_user WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, uid);
        }
        if (contactPhone == null || contactPhone.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写联系电话");
        if (!contactPhone.matches("^1[3-9]\\d{9}$"))
            throw new BizException(ErrorCode.PARAM_ERROR, "联系电话格式不正确");

        String address = trimTo(str(body.get("address")), 255);
        if ("onsite".equals(serviceMode)) {
            if (address == null || address.isBlank())
                throw new BizException(ErrorCode.PARAM_ERROR, "上门服务需要填写服务地址");
        } else {
            address = (String) svc.get("store_address"); // 到店服务记录门店地址，便于展示
        }

        String note = trimTo(str(body.get("note")), 200);

        // ---- 9. 金额与时长 ----
        BigDecimal baseAmount = unitPrice.multiply(BigDecimal.valueOf(days));
        BigDecimal onsiteFee = "onsite".equals(serviceMode)
            ? new BigDecimal(svc.get("onsite_fee").toString()) : BigDecimal.ZERO;
        BigDecimal amount = baseAmount.add(addonAmount).add(onsiteFee).setScale(2, RoundingMode.HALF_UP);

        int duration = dailyPriced ? specDuration * days : specDuration + addonDuration;
        LocalDateTime end = start.plusMinutes(duration);
        String verifyCode = genVerifyCode();

        // ---- 10. 落库 ----
        jdbc.update(
            "INSERT INTO t_appointment (user_id, service_id, service_name, store_id, store_name, category, " +
            " spec_id, spec_name, addon_ids, addon_names, pet_id, pet_name, service_mode, address, " +
            " contact_name, contact_phone, base_amount, addon_amount, onsite_fee, amount, days, duration, " +
            " appointment_time, end_time, verify_code, note, status) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'pending')",
            uid, serviceId, svc.get("name"), svc.get("store_id"), svc.get("store_name"), category,
            specId, specName,
            addonIds.isEmpty() ? null : String.join(",", addonIds.stream().map(String::valueOf).toList()),
            addonNames.isEmpty() ? null : String.join("、", addonNames),
            petId, petName, serviceMode, address,
            contactName, contactPhone, baseAmount, addonAmount, onsiteFee, amount, days, duration,
            start.format(SQL_DT), end.format(SQL_DT), verifyCode, note);

        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", "pending");
        // 费用明细一并回传，下单成功页无需再拉一次详情
        out.put("baseAmount", baseAmount);
        out.put("addonAmount", addonAmount);
        out.put("onsiteFee", onsiteFee);
        out.put("amount", amount);
        out.put("duration", duration);
        out.put("verifyCode", verifyCode);
        out.put("appointmentTime", start.format(SQL_DT));
        out.put("endTime", end.format(SQL_DT));
        return ApiResp.ok(out);
    }

    // ========================================================================
    // 列表 / 详情
    // ========================================================================

    private static final String LIST_COLUMNS =
        "a.id, a.service_id, a.service_name, a.store_id, a.store_name, a.category, a.spec_name, " +
        "a.addon_names, a.pet_id, a.pet_name, a.service_mode, a.address, a.contact_name, a.contact_phone, " +
        "a.base_amount, a.addon_amount, a.onsite_fee, a.amount, a.days, a.duration, a.appointment_time, a.end_time, " +
        "a.verify_code, a.note, a.status, a.cancel_reason, a.reviewed, a.create_time";

    @GetMapping("/appointment/list")
    public ApiResp<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        long uid = TokenContext.requireUserId();
        StringBuilder where = new StringBuilder("WHERE a.user_id = ? AND a.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ?");
            args.add(status);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND a.category = ?");
            args.add(category);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + LIST_COLUMNS + ", si.cover AS service_cover " +
            "FROM t_appointment a LEFT JOIN t_service_item si ON a.service_id = si.id " +
            where + " ORDER BY a.appointment_time DESC, a.id DESC",
            args.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(toAppointment(r));
        return ApiResp.ok(out);
    }

    @GetMapping("/appointment/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT " + LIST_COLUMNS + ", si.cover AS service_cover, s.address AS store_address, " +
            "       mu.phone AS store_phone " +
            "FROM t_appointment a LEFT JOIN t_service_item si ON a.service_id = si.id " +
            "LEFT JOIN t_store s ON a.store_id = s.id " +
            "LEFT JOIN t_user mu ON s.merchant_id = mu.id " +
            "WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在");
        Map<String, Object> m = toAppointment(rows.get(0));
        m.put("storeAddress", rows.get(0).get("store_address"));
        m.put("storePhone", rows.get(0).get("store_phone"));
        return ApiResp.ok(m);
    }

    private Map<String, Object> toAppointment(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        String category = (String) r.get("category");
        m.put("id", r.get("id"));
        m.put("serviceId", r.get("service_id"));
        m.put("serviceName", r.get("service_name"));
        m.put("serviceCover", r.get("service_cover"));
        m.put("storeId", r.get("store_id"));
        m.put("storeName", r.get("store_name"));
        m.put("category", category);
        m.put("icon", ServiceCatalog.icon(category));
        m.put("specName", r.get("spec_name"));
        m.put("addonNames", r.get("addon_names"));
        m.put("petId", r.get("pet_id"));
        m.put("petName", r.get("pet_name"));
        m.put("serviceMode", r.get("service_mode"));
        m.put("address", r.get("address"));
        m.put("contactName", r.get("contact_name"));
        m.put("contactPhone", r.get("contact_phone"));
        m.put("baseAmount", r.get("base_amount"));
        m.put("addonAmount", r.get("addon_amount"));
        m.put("onsiteFee", r.get("onsite_fee"));
        m.put("amount", r.get("amount"));
        m.put("days", r.get("days"));
        m.put("duration", r.get("duration"));
        m.put("verifyCode", r.get("verify_code"));
        m.put("note", r.get("note"));
        m.put("status", r.get("status"));
        m.put("cancelReason", r.get("cancel_reason"));
        m.put("reviewed", toBool(r.get("reviewed")));
        Object at = r.get("appointment_time");
        m.put("appointmentTime", at == null ? null : at.toString());
        Object et = r.get("end_time");
        m.put("endTime", et == null ? null : et.toString());
        Object ct = r.get("create_time");
        m.put("createTime", ct == null ? null : ct.toString());
        return m;
    }

    // ========================================================================
    // 状态变更
    // ========================================================================

    private static final List<String> ALLOWED_STATUS = List.of("cancelled", "done");

    /**
     * 用户侧仅可取消(cancelled)/确认完成(done)。
     * 确认(confirmed)/驳回(rejected) 由商家操作。终态不可再改。
     */
    @PostMapping("/appointment/{id}/status")
    public ApiResp<Map<String, Object>> updateStatus(
        @PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();

        String status = str(body.get("status"));
        if (status == null || status.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "status 必填");
        if (!ALLOWED_STATUS.contains(status))
            throw new BizException(ErrorCode.PARAM_ERROR, "非法 status: " + status);

        List<Map<String, Object>> cur = jdbc.queryForList(
            "SELECT status, service_id, appointment_time FROM t_appointment " +
            "WHERE id = ? AND user_id = ? AND is_deleted = 0", id, uid);
        if (cur.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在或已删除");
        String curStatus = (String) cur.get(0).get("status");
        assertNotFinal(curStatus, "cancelled".equals(status) ? "取消" : "操作");

        String reason = trimTo(str(body.get("cancelReason")), 128);
        if ("cancelled".equals(status)) {
            jdbc.update("UPDATE t_appointment SET status = 'cancelled', cancel_reason = ? WHERE id = ? AND user_id = ?",
                reason == null ? "用户主动取消" : reason, id, uid);
        } else {
            jdbc.update("UPDATE t_appointment SET status = 'done' WHERE id = ? AND user_id = ?", id, uid);
            bumpSales(((Number) cur.get(0).get("service_id")).longValue());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", status);
        return ApiResp.ok(out);
    }

    private void assertNotFinal(String curStatus, String action) {
        if ("cancelled".equals(curStatus) || "done".equals(curStatus) || "rejected".equals(curStatus)) {
            String label = "cancelled".equals(curStatus) ? "取消"
                : "rejected".equals(curStatus) ? "驳回" : "完成";
            throw new BizException(ErrorCode.PARAM_ERROR, "该预约已" + label + "，不可" + action);
        }
    }

    /** 服务完成后销量 +1（真实成交才计入，避免刷单式虚高） */
    private void bumpSales(long serviceId) {
        jdbc.update("UPDATE t_service_item SET sales = sales + 1 WHERE id = ?", serviceId);
    }

    // ========================================================================
    // 服务评价
    // ========================================================================

    /** 完成后评价：写评价表并回写服务的平均分与评价数 */
    @PostMapping("/appointment/{id}/review")
    @Transactional
    public ApiResp<Map<String, Object>> review(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT service_id, store_id, status, reviewed, spec_name FROM t_appointment " +
            "WHERE id = ? AND user_id = ? AND is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在");
        Map<String, Object> a = rows.get(0);
        if (!"done".equals(a.get("status")))
            throw new BizException(ErrorCode.PARAM_ERROR, "服务完成后才能评价");
        if (toBool(a.get("reviewed")))
            throw new BizException(ErrorCode.PARAM_ERROR, "该预约已评价过了");

        Object ratingRaw = body.get("rating");
        int rating = ratingRaw == null ? 5 : ((Number) ratingRaw).intValue();
        if (rating < 1 || rating > 5) throw new BizException(ErrorCode.PARAM_ERROR, "评分需在 1-5 星之间");
        String content = trimTo(str(body.get("content")), 500);
        String images = null;
        Object imgs = body.get("images");
        if (imgs instanceof List<?> l && !l.isEmpty()) {
            List<String> keep = new ArrayList<>();
            for (Object o : l) {
                if (o != null && keep.size() < 6) keep.add(String.valueOf(o));
            }
            images = trimTo(String.join(",", keep), 1000);
        }

        long serviceId = ((Number) a.get("service_id")).longValue();
        long storeId = ((Number) a.get("store_id")).longValue();

        jdbc.update(
            "INSERT INTO t_service_review (service_id, store_id, appointment_id, user_id, rating, content, images, spec_name) " +
            "VALUES (?,?,?,?,?,?,?,?)",
            serviceId, storeId, id, uid, rating, content, images, a.get("spec_name"));
        jdbc.update("UPDATE t_appointment SET reviewed = 1 WHERE id = ?", id);

        // 回写服务评分
        jdbc.update(
            "UPDATE t_service_item SET " +
            " rating = (SELECT IFNULL(ROUND(AVG(rating),2),0) FROM t_service_review WHERE service_id = ? AND is_deleted = 0), " +
            " review_count = (SELECT COUNT(*) FROM t_service_review WHERE service_id = ? AND is_deleted = 0) " +
            "WHERE id = ?", serviceId, serviceId, serviceId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("rating", rating);
        return ApiResp.ok(out);
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static String genVerifyCode() {
        int n = ThreadLocalRandomHolder.RANDOM.nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    private static final class ThreadLocalRandomHolder {
        static final Random RANDOM = new Random();
    }

    private long requireLong(Map<String, Object> body, String key, String msg) {
        Object v = body.get(key);
        if (v == null) throw new BizException(ErrorCode.PARAM_ERROR, msg);
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, msg);
        }
    }

    private Long optLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> parseIdList(Object raw) {
        List<Long> out = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) out.add(n.longValue());
                else if (o != null) {
                    try { out.add(Long.parseLong(String.valueOf(o).trim())); } catch (NumberFormatException ignored) {}
                }
            }
        } else if (raw != null) {
            for (String s : String.valueOf(raw).split(",")) {
                try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        if (out.size() > 10) throw new BizException(ErrorCode.PARAM_ERROR, "加购项最多选择 10 项");
        return out.stream().distinct().toList();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static boolean toBool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return "1".equals(String.valueOf(o)) || "true".equalsIgnoreCase(String.valueOf(o));
    }

    private LocalDateTime parseDateTime(String raw) {
        String norm = raw.trim().replace('T', ' ');
        if (norm.contains(".")) norm = norm.substring(0, norm.indexOf('.'));
        if (norm.endsWith("Z")) norm = norm.substring(0, norm.length() - 1);
        if (norm.length() == 16) norm += ":00";
        try {
            return LocalDateTime.parse(norm.replace(' ', 'T'));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "预约时间格式需为 yyyy-MM-dd HH:mm");
        }
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            if (raw == null || raw.isBlank() || "null".equals(raw)) return fallback;
            String v = raw.trim();
            if (v.length() >= 5) v = v.substring(0, 5);
            return LocalTime.parse(v);
        } catch (Exception e) {
            return fallback;
        }
    }
}
