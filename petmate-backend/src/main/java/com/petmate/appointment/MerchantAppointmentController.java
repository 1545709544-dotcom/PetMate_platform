package com.petmate.appointment;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.NotifyService;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家端预约管理：打通「线上预约 → 到店核销 → 服务完成 → 评价」全链路。
 *  - GET  /merchant/appointments               本商家名下门店的预约单（状态/日期/关键词筛选 + 各状态计数）
 *  - GET  /merchant/appointment/{id}           预约详情（规格 / 加购 / 宠物 / 费用明细 / 核销码）
 *  - POST /merchant/appointment/{id}/status    确认 / 完成 / 取消 / 拒绝
 *  - POST /merchant/appointment/{id}/reschedule 改期（校验营业时段与余位）
 *  - POST /merchant/appointment/verify         按核销码到店核销（到店支付模式的收款凭证）
 * 仅商家可见，且只能操作自己门店的预约。
 */
@RestController
public class MerchantAppointmentController {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(MerchantAppointmentController.class);

    private final JdbcTemplate jdbc;
    private final NotifyService notify;

    public MerchantAppointmentController(JdbcTemplate jdbc, NotifyService notify) {
        this.jdbc = jdbc;
        this.notify = notify;
    }

    /** 当前登录商家名下的门店 id 列表 */
    private List<Long> myStoreIds() {
        long uid = TokenContext.requireUserId();
        TokenContext.requireRoleAndActive("merchant");
        return jdbc.query("SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0",
            (rs, i) -> rs.getLong(1), uid);
    }

    private String storeIn() {
        List<Long> ids = myStoreIds();
        if (ids.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "请先创建门店");
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @GetMapping("/merchant/appointments")
    public ApiResp<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long serviceId) {
        List<Long> storeIds = myStoreIds();
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("list", List.of());
        empty.put("total", 0);
        empty.put("stat", Map.of());
        if (storeIds.isEmpty()) return ApiResp.ok(empty);

        String inClause = storeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        StringBuilder where = new StringBuilder(
            "WHERE a.store_id IN (" + inClause + ") AND a.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            if ("todo".equals(status)) {
                where.append(" AND a.status IN ('pending','confirmed')");
            } else {
                where.append(" AND a.status = ?");
                args.add(status);
            }
        }
        if (date != null && !date.isBlank()) {
            where.append(" AND DATE(a.appointment_time) = ?");
            args.add(date.trim());
        }
        if (serviceId != null) {
            where.append(" AND a.service_id = ?");
            args.add(serviceId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (a.service_name LIKE ? OR a.contact_name LIKE ? OR a.contact_phone LIKE ? " +
                "OR a.pet_name LIKE ? OR a.verify_code LIKE ? OR u.nickname LIKE ? OR u.phone LIKE ?)");
            for (int i = 0; i < 7; i++) args.add(kw);
        }

        String sql =
            "SELECT a.id, a.service_id, a.service_name, a.category, a.store_name, a.appointment_time, a.end_time, " +
            "a.spec_name, a.addon_names, a.pet_name, a.service_mode, a.address, a.days, a.duration, " +
            "a.contact_name, a.contact_phone, a.base_amount, a.addon_amount, a.onsite_fee, a.amount, " +
            "a.verify_code, a.reviewed, a.note, a.cancel_reason, a.status, a.create_time, " +
            "u.nickname AS user_name, u.phone AS user_phone, u.avatar AS user_avatar, " +
            "si.cover AS service_cover " +
            "FROM t_appointment a JOIN t_user u ON a.user_id = u.id " +
            "LEFT JOIN t_service_item si ON a.service_id = si.id " +
            where + " ORDER BY a.appointment_time DESC, a.id DESC LIMIT 300";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(toDto(r));

        // Tab 角标计数
        Map<String, Object> stat = jdbc.queryForMap(
            "SELECT " +
            "COALESCE(SUM(CASE WHEN status='pending' THEN 1 ELSE 0 END),0) AS pendingCount, " +
            "COALESCE(SUM(CASE WHEN status='confirmed' THEN 1 ELSE 0 END),0) AS confirmedCount, " +
            "COALESCE(SUM(CASE WHEN status IN ('pending','confirmed') THEN 1 ELSE 0 END),0) AS todoCount, " +
            "COALESCE(SUM(CASE WHEN status='done' THEN 1 ELSE 0 END),0) AS doneCount, " +
            "COALESCE(SUM(CASE WHEN status='cancelled' THEN 1 ELSE 0 END),0) AS cancelledCount, " +
            "COALESCE(SUM(CASE WHEN status='rejected' THEN 1 ELSE 0 END),0) AS rejectedCount, " +
            "COALESCE(SUM(CASE WHEN DATE(appointment_time)=CURDATE() AND status IN ('pending','confirmed') " +
            "  THEN 1 ELSE 0 END),0) AS todayCount, " +
            "COALESCE(SUM(CASE WHEN status='done' THEN amount ELSE 0 END),0) AS doneAmount " +
            "FROM t_appointment WHERE store_id IN (" + inClause + ") AND is_deleted = 0");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", out);
        data.put("total", out.size());
        data.put("stat", stat);
        return ApiResp.ok(data);
    }

    /** 预约详情：给商家看的完整工单（规格 / 加购 / 宠物 / 上门地址 / 费用明细 / 核销码） */
    @GetMapping("/merchant/appointment/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        String in = storeIn();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.*, u.nickname AS user_name, u.phone AS user_phone, u.avatar AS user_avatar, " +
            "si.cover AS service_cover, si.notice, si.refund_policy " +
            "FROM t_appointment a JOIN t_user u ON a.user_id = u.id " +
            "LEFT JOIN t_service_item si ON a.service_id = si.id " +
            "WHERE a.id = ? AND a.store_id IN (" + in + ") AND a.is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在或不属于当前商家");
        Map<String, Object> r = rows.get(0);
        Map<String, Object> m = toDto(r);
        m.put("userId", r.get("user_id"));
        m.put("storeId", r.get("store_id"));
        m.put("specId", r.get("spec_id"));
        m.put("petId", r.get("pet_id"));
        m.put("notice", r.get("notice"));
        m.put("refundPolicy", r.get("refund_policy"));

        // 宠物档案（如果用户关联了宠物）
        Object petId = r.get("pet_id");
        if (petId != null) {
            List<Map<String, Object>> pets = jdbc.queryForList(
                "SELECT p.id, p.name, p.breed_name AS breedName, p.gender, p.weight, p.birthday, " +
                "p.avatar, p.neutered, p.description, c.name AS categoryName " +
                "FROM t_pet p LEFT JOIN t_pet_category c ON p.category_id = c.id " +
                "WHERE p.id = ? AND p.is_deleted = 0", ((Number) petId).longValue());
            if (!pets.isEmpty()) m.put("pet", pets.get(0));
        }
        // 用户评价（已评价时展示）
        List<Map<String, Object>> reviews = jdbc.queryForList(
            "SELECT rating, content, images, reply, created_at FROM t_service_review " +
            "WHERE appointment_id = ? AND is_deleted = 0", id);
        m.put("review", reviews.isEmpty() ? null : reviews.get(0));
        return ApiResp.ok(m);
    }

    private Map<String, Object> toDto(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("serviceId", r.get("service_id"));
        m.put("serviceName", r.get("service_name"));
        m.put("serviceCover", r.get("service_cover"));
        m.put("category", r.get("category"));
        m.put("storeName", r.get("store_name"));
        m.put("specName", r.get("spec_name"));
        m.put("addonNames", r.get("addon_names"));
        m.put("petName", r.get("pet_name"));
        m.put("serviceMode", r.get("service_mode") == null ? "instore" : r.get("service_mode"));
        m.put("address", r.get("address"));
        m.put("days", r.get("days"));
        m.put("duration", r.get("duration"));
        m.put("contactName", r.get("contact_name"));
        m.put("contactPhone", r.get("contact_phone"));
        m.put("baseAmount", r.get("base_amount"));
        m.put("addonAmount", r.get("addon_amount"));
        m.put("onsiteFee", r.get("onsite_fee"));
        m.put("amount", r.get("amount"));
        m.put("verifyCode", r.get("verify_code"));
        m.put("reviewed", toBool(r.get("reviewed")));
        m.put("userName", r.get("user_name"));
        m.put("userPhone", r.get("user_phone"));
        m.put("userAvatar", r.get("user_avatar"));
        m.put("appointmentTime", r.get("appointment_time") == null ? null : r.get("appointment_time").toString());
        m.put("endTime", r.get("end_time") == null ? null : r.get("end_time").toString());
        m.put("note", r.get("note"));
        m.put("cancelReason", r.get("cancel_reason"));
        m.put("status", r.get("status"));
        m.put("createTime", r.get("create_time") == null ? null : r.get("create_time").toString());
        return m;
    }

    /** 商家可置的状态：确认(进行中)/完成/取消/拒绝 */
    private static final List<String> MERCHANT_ALLOWED =
        List.of("confirmed", "done", "cancelled", "rejected");

    @PostMapping("/merchant/appointment/{id}/status")
    @Transactional
    public ApiResp<Map<String, Object>> updateStatus(
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        String storeIn = storeIn();

        String status = (String) body.get("status");
        if (status == null || status.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "status 必填");
        if (!MERCHANT_ALLOWED.contains(status))
            throw new BizException(ErrorCode.PARAM_ERROR, "非法 status: " + status);
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason")).trim();
        if (reason != null && reason.length() > 200) reason = reason.substring(0, 200);
        if (("rejected".equals(status) || "cancelled".equals(status)) && (reason == null || reason.isEmpty()))
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写" + ("rejected".equals(status) ? "拒绝" : "取消") + "原因");

        // 归属校验 + 当前状态
        List<Map<String, Object>> cur = jdbc.queryForList(
            "SELECT status, user_id, service_id, service_name, amount, verify_code " +
            "FROM t_appointment WHERE id = ? AND store_id IN (" + storeIn + ") AND is_deleted = 0", id);
        if (cur.isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在或不属于当前商家");
        Map<String, Object> ap = cur.get(0);
        String curStatus = (String) ap.get("status");
        if ("cancelled".equals(curStatus) || "done".equals(curStatus) || "rejected".equals(curStatus))
            throw new BizException(ErrorCode.PARAM_ERROR,
                "该预约已" + statusText(curStatus) + "，不可修改");
        // pending 不能跳过确认直接完成（保证「确认 → 到店核销 → 完成」链路）
        if ("done".equals(status) && !"confirmed".equals(curStatus))
            throw new BizException(ErrorCode.PARAM_ERROR, "请先确认预约，客户到店核销后再标记完成");

        jdbc.update("UPDATE t_appointment SET status = ?, cancel_reason = ? WHERE id = ?",
            status, reason, id);

        long uid = ((Number) ap.get("user_id")).longValue();
        String svc = String.valueOf(ap.get("service_name"));
        Object serviceId = ap.get("service_id");

        switch (status) {
            case "confirmed" -> notify.notifyUser(uid, "appointment", "预约已确认",
                "您预约的「" + svc + "」已被商家确认，到店出示核销码 "
                    + (ap.get("verify_code") == null ? "" : ap.get("verify_code")) + " 即可开始服务。",
                String.valueOf(id));
            case "done" -> {
                if (serviceId != null) bumpSales(((Number) serviceId).longValue());
                notify.notifyUser(uid, "appointment", "服务已完成",
                    "「" + svc + "」服务已完成，快去评价一下吧～", String.valueOf(id));
            }
            case "rejected" -> notify.notifyUser(uid, "appointment", "预约申请未通过",
                "您预约的「" + svc + "」未通过商家审核：" + reason + "，可重新选择时间或联系门店。",
                String.valueOf(id));
            case "cancelled" -> notify.notifyUser(uid, "appointment", "预约已取消",
                "您预约的「" + svc + "」已被商家取消：" + reason, String.valueOf(id));
            default -> { }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", status);
        return ApiResp.ok(out);
    }

    /**
     * 到店核销：客户报 6 位核销码，商家一键确认服务开始/完成。
     * 到店支付模式下这就是收款凭证 —— 核销即视为服务履约完成。
     */
    @PostMapping("/merchant/appointment/verify")
    @Transactional
    public ApiResp<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        String storeIn = storeIn();
        String code = body.get("code") == null ? null : String.valueOf(body.get("code")).trim();
        if (code == null || code.isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入核销码");
        if (!code.matches("\\d{6}"))
            throw new BizException(ErrorCode.PARAM_ERROR, "核销码为 6 位数字");

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, service_id, service_name, spec_name, addon_names, pet_name, " +
            "appointment_time, amount, status FROM t_appointment " +
            "WHERE verify_code = ? AND store_id IN (" + storeIn + ") AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT 1 FOR UPDATE", code);
        if (rows.isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "核销码无效，请核对后重试");
        Map<String, Object> ap = rows.get(0);
        String st = String.valueOf(ap.get("status"));
        if ("done".equals(st)) throw new BizException(ErrorCode.PARAM_ERROR, "该预约已核销完成，请勿重复操作");
        if ("cancelled".equals(st) || "rejected".equals(st))
            throw new BizException(ErrorCode.PARAM_ERROR, "该预约已" + statusText(st) + "，无法核销");

        long id = ((Number) ap.get("id")).longValue();
        jdbc.update("UPDATE t_appointment SET status = 'done' WHERE id = ?", id);
        Object serviceId = ap.get("service_id");
        if (serviceId != null) bumpSales(((Number) serviceId).longValue());

        long uid = ((Number) ap.get("user_id")).longValue();
        notify.notifyUser(uid, "appointment", "核销成功",
            "「" + ap.get("service_name") + "」已核销完成，感谢您的光临，欢迎评价～", String.valueOf(id));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("serviceName", ap.get("service_name"));
        out.put("specName", ap.get("spec_name"));
        out.put("addonNames", ap.get("addon_names"));
        out.put("petName", ap.get("pet_name"));
        out.put("amount", ap.get("amount"));
        out.put("appointmentTime", ap.get("appointment_time") == null ? null : ap.get("appointment_time").toString());
        out.put("status", "done");
        return ApiResp.ok(out);
    }

    /**
     * 商家改期：更新预约时间。
     * 仅允许对自己门店、且处于非终态(pending/confirmed)的预约改期，并校验新时段是否还有余位。
     */
    @PostMapping("/merchant/appointment/{id}/reschedule")
    @Transactional
    public ApiResp<Map<String, Object>> reschedule(
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        String storeIn = storeIn();

        String raw = (String) body.get("appointmentTime");
        if (raw == null || raw.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "appointmentTime 必填");
        String norm = raw.trim().replace('T', ' ');
        if (norm.contains(".")) norm = norm.substring(0, norm.indexOf('.'));
        if (norm.contains("+")) norm = norm.substring(0, norm.indexOf('+'));
        if (norm.endsWith("Z")) norm = norm.substring(0, norm.length() - 1);
        if (norm.length() == 16) norm += ":00"; // yyyy-MM-dd HH:mm -> 补秒
        java.sql.Timestamp ts;
        try {
            ts = java.sql.Timestamp.valueOf(norm);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                "appointmentTime 格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
        if (ts.toLocalDateTime().isBefore(java.time.LocalDateTime.now()))
            throw new BizException(ErrorCode.PARAM_ERROR, "改期时间不能早于当前时间");

        List<Map<String, Object>> cur = jdbc.queryForList(
            "SELECT status, user_id, service_id, service_name, duration " +
            "FROM t_appointment WHERE id = ? AND store_id IN (" + storeIn + ") AND is_deleted = 0", id);
        if (cur.isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "预约不存在或不属于当前商家");
        Map<String, Object> r = cur.get(0);
        String curStatus = (String) r.get("status");
        if ("cancelled".equals(curStatus) || "done".equals(curStatus) || "rejected".equals(curStatus))
            throw new BizException(ErrorCode.PARAM_ERROR, "该预约已" + statusText(curStatus) + "，不可改期");

        // 余位校验：同服务同时段不得超过 slot_capacity
        Object sid = r.get("service_id");
        if (sid != null) {
            long serviceId = ((Number) sid).longValue();
            List<Map<String, Object>> cfg = jdbc.queryForList(
                "SELECT slot_capacity FROM t_service_item WHERE id = ? AND is_deleted = 0", serviceId);
            int cap = cfg.isEmpty() || cfg.get(0).get("slot_capacity") == null
                ? 2 : ((Number) cfg.get(0).get("slot_capacity")).intValue();
            Integer used = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_appointment WHERE service_id = ? AND appointment_time = ? " +
                "AND status IN ('pending','confirmed') AND is_deleted = 0 AND id <> ?",
                Integer.class, serviceId, ts, id);
            if (used != null && used >= cap)
                throw new BizException(ErrorCode.PARAM_ERROR,
                    "该时段已约满（容量 " + cap + "），请换一个时间");
        }

        // 同步顺延结束时间
        Object dur = r.get("duration");
        java.sql.Timestamp endTs = dur == null ? null
            : java.sql.Timestamp.valueOf(ts.toLocalDateTime().plusMinutes(((Number) dur).longValue()));
        jdbc.update("UPDATE t_appointment SET appointment_time = ?, end_time = ? WHERE id = ?", ts, endTs, id);

        long uid = ((Number) r.get("user_id")).longValue();
        String svc = String.valueOf(r.get("service_name"));
        notify.notifyUser(uid, "appointment", "预约时间已调整",
            "您预约的「" + svc + "」时间已更新为 " + norm, String.valueOf(id));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("appointmentTime", norm);
        out.put("status", curStatus);
        return ApiResp.ok(out);
    }

    /** 商家回复服务评价（t_service_review，与商品评价 /merchant/review 区分） */
    @PostMapping("/merchant/reviews/{id}/reply")
    public ApiResp<Void> replyReview(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String storeIn = storeIn();
        String reply = body.get("reply") == null ? null : String.valueOf(body.get("reply")).trim();
        if (reply == null || reply.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "回复内容不能为空");
        if (reply.length() > 300) reply = reply.substring(0, 300);
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_service_review WHERE id = ? AND store_id IN (" + storeIn + ") AND is_deleted = 0",
            Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.NO_PERMISSION, "评价不存在或不属于本门店");
        jdbc.update("UPDATE t_service_review SET reply = ? WHERE id = ?", reply, id);
        return ApiResp.ok();
    }

    /** 商家侧评价列表（可按服务筛选 / 只看未回复） */
    @GetMapping("/merchant/reviews")
    public ApiResp<Map<String, Object>> reviews(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "false") boolean unrepliedOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String in = storeIn();
        StringBuilder where = new StringBuilder(
            "FROM t_service_review r LEFT JOIN t_user u ON r.user_id = u.id " +
            "LEFT JOIN t_service_item si ON r.service_id = si.id " +
            "WHERE r.store_id IN (" + in + ") AND r.is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (serviceId != null) { where.append("AND r.service_id = ? "); args.add(serviceId); }
        if (unrepliedOnly) where.append("AND (r.reply IS NULL OR r.reply = '') ");

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, args.toArray());
        int sz = Math.min(Math.max(size, 1), 50);
        int offset = (Math.max(page, 1) - 1) * sz;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(sz);
        pageArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.service_id, r.appointment_id, r.rating, r.content, r.images, r.spec_name, " +
            "r.reply, r.created_at, u.nickname, u.avatar, si.name AS service_name " +
            where + "ORDER BY r.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("serviceId", r.get("service_id"));
            m.put("serviceName", r.get("service_name"));
            m.put("appointmentId", r.get("appointment_id"));
            m.put("rating", r.get("rating"));
            m.put("content", r.get("content"));
            m.put("images", r.get("images"));
            m.put("specName", r.get("spec_name"));
            m.put("reply", r.get("reply"));
            m.put("nickname", r.get("nickname"));
            m.put("avatar", r.get("avatar"));
            m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
            out.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", out);
        data.put("total", total == null ? 0 : total);
        return ApiResp.ok(data);
    }

    private void bumpSales(long serviceId) {
        jdbc.update("UPDATE t_service_item SET sales = COALESCE(sales, 0) + 1 WHERE id = ?", serviceId);
    }

    private static String statusText(String s) {
        return switch (s == null ? "" : s) {
            case "cancelled" -> "取消";
            case "done" -> "完成";
            case "rejected" -> "拒绝";
            case "confirmed" -> "确认";
            default -> "处理";
        };
    }

    private static boolean toBool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return "1".equals(String.valueOf(o)) || "true".equalsIgnoreCase(String.valueOf(o));
    }
}
