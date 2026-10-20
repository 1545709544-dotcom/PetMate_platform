package com.petmate.merchant;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 商家端扩展：服务项 CRUD、排班（store.business_hours）、相册（store.photos）、
 * 商家订单详情（复用 OrderController 路径不通的 /merchant/order/{orderNo}）。
 * 不增加新表，沿用 t_service_item / t_store / t_order / t_order_item。
 */
@RestController
public class MerchantOpsController {

    private final JdbcTemplate jdbc;
    private final com.petmate.common.AuditService audit;
    private final com.petmate.refund.RefundLogService refundLog;
    private final com.petmate.common.NotifyService notify;
    private final com.petmate.points.PointsService pointsService;

    public MerchantOpsController(JdbcTemplate jdbc, com.petmate.common.AuditService audit,
                                 com.petmate.refund.RefundLogService refundLog,
                                 com.petmate.common.NotifyService notify,
                                 com.petmate.points.PointsService pointsService) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.refundLog = refundLog;
        this.notify = notify;
        this.pointsService = pointsService;
    }

    private long merchantId() {
        TokenContext.requireRoleAndActive("merchant");
        return TokenContext.requireMerchantId();
    }

    private String currentOperator() {
        Long sid = TokenContext.getStaffId();
        if (sid != null) {
            String n = jdbc.queryForObject("SELECT nickname FROM t_merchant_staff WHERE id = ?", String.class, sid);
            return n != null ? n : "员工";
        }
        String n = jdbc.queryForObject("SELECT nickname FROM t_user WHERE id = ?", String.class, TokenContext.requireUserId());
        return n != null ? n : "店主";
    }

    private long storeId() {
        long uid = merchantId();
        List<Long> ids = jdbc.query(
            "SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0 ORDER BY id LIMIT 1",
            (rs, i) -> rs.getLong(1), uid);
        if (ids.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "请先创建门店");
        return ids.get(0);
    }

    // ============ 服务项 CRUD（v13 行业化：规格分档 + 加购项 + 时段容量 + 须知/退改） ============

    /** 商家编辑表单用的标准分类字典：一级 + 二级 + 图标 + 计价单位 */
    @GetMapping("/merchant/service/catalog")
    public ApiResp<Map<String, Object>> serviceCatalog() {
        merchantId();
        List<Map<String, Object>> cats = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : com.petmate.service.ServiceCatalog.SUBS.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("subs", e.getValue());
            m.put("icon", com.petmate.service.ServiceCatalog.icon(e.getKey()));
            m.put("priceUnit", com.petmate.service.ServiceCatalog.priceUnit(e.getKey()));
            m.put("dailyPriced", com.petmate.service.ServiceCatalog.isDailyPriced(e.getKey()));
            cats.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", cats);
        out.put("petTypes", List.of(
            Map.of("value", "all", "label", "猫狗通用"),
            Map.of("value", "dog", "label", "仅限狗狗"),
            Map.of("value", "cat", "label", "仅限猫咪")));
        out.put("serviceModes", List.of(
            Map.of("value", "instore", "label", "到店服务"),
            Map.of("value", "onsite", "label", "上门服务")));
        out.put("coats", List.of("不限", "短毛", "长毛"));
        out.put("petKinds", List.of("all", "dog", "cat"));
        return ApiResp.ok(out);
    }

    @GetMapping("/merchant/service/{id}")
    public ApiResp<Map<String, Object>> getService(@PathVariable long id) {
        long uid = merchantId();
        // 校验服务归属：服务 → store.merchant_id == uid
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT si.id, si.store_id, si.name, si.category, si.sub_category, si.service_mode, si.pet_type, " +
            "si.price, si.original_price, si.onsite_fee, si.duration, si.cover, si.description, si.status, " +
            "si.sales, si.rating, si.review_count, si.tags, si.include_items, si.notice, si.refund_policy, " +
            "si.open_time, si.close_time, si.slot_minutes, si.slot_capacity, si.advance_days " +
            "FROM t_service_item si JOIN t_store s ON si.store_id = s.id " +
            "WHERE si.id = ? AND s.merchant_id = ? AND si.is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "服务不存在或不属于当前商家");
        Map<String, Object> r = rows.get(0);
        String category = str(r.get("category"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.get("id"));
        out.put("storeId", r.get("store_id"));
        out.put("name", r.get("name"));
        out.put("category", category);
        out.put("subCategory", r.get("sub_category"));
        out.put("serviceMode", r.get("service_mode") == null ? "instore" : r.get("service_mode"));
        out.put("petType", r.get("pet_type") == null ? "all" : r.get("pet_type"));
        out.put("price", r.get("price"));
        out.put("originalPrice", r.get("original_price"));
        out.put("onsiteFee", r.get("onsite_fee"));
        out.put("duration", r.get("duration"));
        out.put("cover", r.get("cover"));
        out.put("description", r.get("description"));
        out.put("status", r.get("status"));
        out.put("sales", r.get("sales"));
        out.put("rating", r.get("rating"));
        out.put("reviewCount", r.get("review_count"));
        out.put("tags", r.get("tags"));
        out.put("includeItems", r.get("include_items"));
        out.put("notice", r.get("notice"));
        out.put("refundPolicy", r.get("refund_policy"));
        out.put("openTime", timeStr(r.get("open_time"), "09:00"));
        out.put("closeTime", timeStr(r.get("close_time"), "20:00"));
        out.put("slotMinutes", r.get("slot_minutes") == null ? 60 : r.get("slot_minutes"));
        out.put("slotCapacity", r.get("slot_capacity") == null ? 2 : r.get("slot_capacity"));
        out.put("advanceDays", r.get("advance_days") == null ? 7 : r.get("advance_days"));
        out.put("priceUnit", com.petmate.service.ServiceCatalog.priceUnit(category));
        out.put("dailyPriced", com.petmate.service.ServiceCatalog.isDailyPriced(category));
        out.put("specs", jdbc.queryForList(
            "SELECT id, name, pet_kind AS petKind, coat, weight_min AS weightMin, weight_max AS weightMax, " +
            "price, duration, sort_no AS sortNo FROM t_service_spec " +
            "WHERE service_id = ? AND is_deleted = 0 ORDER BY sort_no, id", id));
        out.put("addons", jdbc.queryForList(
            "SELECT id, name, price, duration, description, sort_no AS sortNo FROM t_service_addon " +
            "WHERE service_id = ? AND is_deleted = 0 ORDER BY sort_no, id", id));
        return ApiResp.ok(out);
    }

    @PostMapping("/merchant/service")
    @Transactional
    public ApiResp<Map<String, Object>> createService(@RequestBody Map<String, Object> body) {
        long storeId = storeId();
        String name = trimTo(str(body.get("name")), 60);
        String category = str(body.get("category"));
        if (name == null || name.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "服务名称必填");
        if (!com.petmate.service.ServiceCatalog.isValidCategory(category))
            throw new BizException(ErrorCode.PARAM_ERROR, "分类不合法，请选择标准分类");
        String subCategory = trimTo(str(body.get("subCategory")), 30);
        if (!com.petmate.service.ServiceCatalog.isValidSub(category, subCategory))
            throw new BizException(ErrorCode.PARAM_ERROR, "二级子类不属于「" + category + "」");

        jdbc.update(
            "INSERT INTO t_service_item (store_id, name, category, sub_category, service_mode, pet_type, " +
            "price, original_price, onsite_fee, duration, cover, description, status, tags, include_items, " +
            "notice, refund_policy, open_time, close_time, slot_minutes, slot_capacity, advance_days) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            storeId, name, category, subCategory,
            mode(body.get("serviceMode")), petType(body.get("petType")),
            money(body.get("price"), BigDecimal.ZERO), money(body.get("originalPrice"), null),
            money(body.get("onsiteFee"), BigDecimal.ZERO),
            intOr(body.get("duration"), 60),
            trimTo(str(body.get("cover")), 255), str(body.get("description")),
            intOr(body.get("status"), 1),
            trimTo(str(body.get("tags")), 120), str(body.get("includeItems")),
            str(body.get("notice")), str(body.get("refundPolicy")),
            timeOr(body.get("openTime"), "09:00:00"), timeOr(body.get("closeTime"), "20:00:00"),
            clamp(intOr(body.get("slotMinutes"), 60), 15, 480),
            clamp(intOr(body.get("slotCapacity"), 2), 1, 99),
            clamp(intOr(body.get("advanceDays"), 7), 1, 90));
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        saveSpecs(id, body.get("specs"));
        saveAddons(id, body.get("addons"));
        syncStartPrice(id);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    @PutMapping("/merchant/service/{id}")
    @Transactional
    public ApiResp<Void> updateService(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long storeId = storeId();
        List<Map<String, Object>> own = jdbc.queryForList(
            "SELECT category FROM t_service_item WHERE id = ? AND store_id = ? AND is_deleted = 0", id, storeId);
        if (own.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "服务不存在或不属于本门店");
        String category = str(own.get(0).get("category"));

        StringBuilder sql = new StringBuilder("UPDATE t_service_item SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;

        if (body.containsKey("category")) {
            String c = str(body.get("category"));
            if (!com.petmate.service.ServiceCatalog.isValidCategory(c))
                throw new BizException(ErrorCode.PARAM_ERROR, "分类不合法，请选择标准分类");
            category = c;
            sql.append("category = ?"); args.add(c); first = false;
        }
        if (body.containsKey("subCategory")) {
            String sub = trimTo(str(body.get("subCategory")), 30);
            if (!com.petmate.service.ServiceCatalog.isValidSub(category, sub))
                throw new BizException(ErrorCode.PARAM_ERROR, "二级子类不属于「" + category + "」");
            sql.append(first ? "" : ", ").append("sub_category = ?"); args.add(sub); first = false;
        }
        if (body.containsKey("name")) {
            String n = trimTo(str(body.get("name")), 60);
            if (n == null || n.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "服务名称不能为空");
            sql.append(first ? "" : ", ").append("name = ?"); args.add(n); first = false;
        }
        if (body.containsKey("serviceMode"))   { sql.append(first ? "" : ", ").append("service_mode = ?"); args.add(mode(body.get("serviceMode"))); first = false; }
        if (body.containsKey("petType"))       { sql.append(first ? "" : ", ").append("pet_type = ?"); args.add(petType(body.get("petType"))); first = false; }
        if (body.containsKey("price"))         { sql.append(first ? "" : ", ").append("price = ?"); args.add(money(body.get("price"), BigDecimal.ZERO)); first = false; }
        if (body.containsKey("originalPrice")) { sql.append(first ? "" : ", ").append("original_price = ?"); args.add(money(body.get("originalPrice"), null)); first = false; }
        if (body.containsKey("onsiteFee"))     { sql.append(first ? "" : ", ").append("onsite_fee = ?"); args.add(money(body.get("onsiteFee"), BigDecimal.ZERO)); first = false; }
        if (body.containsKey("duration"))      { sql.append(first ? "" : ", ").append("duration = ?"); args.add(clamp(intOr(body.get("duration"), 60), 10, 1440)); first = false; }
        if (body.containsKey("cover"))         { sql.append(first ? "" : ", ").append("cover = ?"); args.add(trimTo(str(body.get("cover")), 255)); first = false; }
        if (body.containsKey("description"))   { sql.append(first ? "" : ", ").append("description = ?"); args.add(str(body.get("description"))); first = false; }
        if (body.containsKey("status"))        { sql.append(first ? "" : ", ").append("status = ?"); args.add(intOr(body.get("status"), 1)); first = false; }
        if (body.containsKey("tags"))          { sql.append(first ? "" : ", ").append("tags = ?"); args.add(trimTo(str(body.get("tags")), 120)); first = false; }
        if (body.containsKey("includeItems"))  { sql.append(first ? "" : ", ").append("include_items = ?"); args.add(str(body.get("includeItems"))); first = false; }
        if (body.containsKey("notice"))        { sql.append(first ? "" : ", ").append("notice = ?"); args.add(str(body.get("notice"))); first = false; }
        if (body.containsKey("refundPolicy"))  { sql.append(first ? "" : ", ").append("refund_policy = ?"); args.add(str(body.get("refundPolicy"))); first = false; }
        if (body.containsKey("openTime"))      { sql.append(first ? "" : ", ").append("open_time = ?"); args.add(timeOr(body.get("openTime"), "09:00:00")); first = false; }
        if (body.containsKey("closeTime"))     { sql.append(first ? "" : ", ").append("close_time = ?"); args.add(timeOr(body.get("closeTime"), "20:00:00")); first = false; }
        if (body.containsKey("slotMinutes"))   { sql.append(first ? "" : ", ").append("slot_minutes = ?"); args.add(clamp(intOr(body.get("slotMinutes"), 60), 15, 480)); first = false; }
        if (body.containsKey("slotCapacity"))  { sql.append(first ? "" : ", ").append("slot_capacity = ?"); args.add(clamp(intOr(body.get("slotCapacity"), 2), 1, 99)); first = false; }
        if (body.containsKey("advanceDays"))   { sql.append(first ? "" : ", ").append("advance_days = ?"); args.add(clamp(intOr(body.get("advanceDays"), 7), 1, 90)); first = false; }

        boolean touchSpecs = body.containsKey("specs");
        boolean touchAddons = body.containsKey("addons");
        if (first && !touchSpecs && !touchAddons)
            throw new BizException(ErrorCode.PARAM_ERROR, "无可更新字段");

        if (!first) {
            sql.append(" WHERE id = ?");
            args.add(id);
            jdbc.update(sql.toString(), args.toArray());
        }
        if (touchSpecs)  saveSpecs(id, body.get("specs"));
        if (touchAddons) saveAddons(id, body.get("addons"));
        if (touchSpecs)  syncStartPrice(id);
        return ApiResp.ok();
    }

    @DeleteMapping("/merchant/service/{id}")
    @Transactional
    public ApiResp<Void> deleteService(@PathVariable long id) {
        long storeId = storeId();
        Integer own = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_service_item WHERE id = ? AND store_id = ? AND is_deleted = 0",
            Integer.class, id, storeId);
        if (own == null || own == 0) throw new BizException(ErrorCode.PARAM_ERROR, "服务不存在或不属于本门店");
        // 有未完成预约时不允许直接下架删除，避免用户订单悬空
        Integer live = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_appointment WHERE service_id = ? AND is_deleted = 0 " +
            "AND status IN ('pending','confirmed')", Integer.class, id);
        if (live != null && live > 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "该服务还有 " + live + " 个进行中的预约，请先处理后再删除");
        jdbc.update("UPDATE t_service_item SET is_deleted = 1 WHERE id = ?", id);
        jdbc.update("UPDATE t_service_spec SET is_deleted = 1 WHERE service_id = ?", id);
        jdbc.update("UPDATE t_service_addon SET is_deleted = 1 WHERE service_id = ?", id);
        return ApiResp.ok();
    }

    /** 全量覆盖规格档位：软删旧的 → 插入新的（前端传完整数组） */
    @SuppressWarnings("unchecked")
    private void saveSpecs(long serviceId, Object raw) {
        List<Map<String, Object>> list = raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
        if (list.size() > 30) throw new BizException(ErrorCode.PARAM_ERROR, "规格档位最多 30 个");
        jdbc.update("UPDATE t_service_spec SET is_deleted = 1 WHERE service_id = ?", serviceId);
        int sort = 0;
        for (Map<String, Object> s : list) {
            String name = trimTo(str(s.get("name")), 40);
            if (name == null || name.isBlank()) continue;
            BigDecimal price = money(s.get("price"), BigDecimal.ZERO);
            if (price.compareTo(BigDecimal.ZERO) < 0)
                throw new BizException(ErrorCode.PARAM_ERROR, "规格「" + name + "」价格不能为负");
            jdbc.update(
                "INSERT INTO t_service_spec (service_id, name, pet_kind, coat, weight_min, weight_max, " +
                "price, duration, sort_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                serviceId, name, petKind(s.get("petKind")), trimTo(str(s.get("coat")), 10),
                money(s.get("weightMin"), null), money(s.get("weightMax"), null),
                price, clamp(intOr(s.get("duration"), 60), 10, 1440), ++sort);
        }
    }

    /** 全量覆盖加购项 */
    @SuppressWarnings("unchecked")
    private void saveAddons(long serviceId, Object raw) {
        List<Map<String, Object>> list = raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
        if (list.size() > 20) throw new BizException(ErrorCode.PARAM_ERROR, "加购项最多 20 个");
        jdbc.update("UPDATE t_service_addon SET is_deleted = 1 WHERE service_id = ?", serviceId);
        int sort = 0;
        for (Map<String, Object> a : list) {
            String name = trimTo(str(a.get("name")), 40);
            if (name == null || name.isBlank()) continue;
            BigDecimal price = money(a.get("price"), BigDecimal.ZERO);
            if (price.compareTo(BigDecimal.ZERO) < 0)
                throw new BizException(ErrorCode.PARAM_ERROR, "加购项「" + name + "」价格不能为负");
            jdbc.update(
                "INSERT INTO t_service_addon (service_id, name, price, duration, description, sort_no) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                serviceId, name, price, clamp(intOr(a.get("duration"), 0), 0, 480),
                trimTo(str(a.get("description")), 120), ++sort);
        }
    }

    /** 列表页「起价」= 最低档位价；无档位时保留商家填写的 price */
    private void syncStartPrice(long serviceId) {
        List<Map<String, Object>> agg = jdbc.queryForList(
            "SELECT MIN(price) AS p, MIN(duration) AS d FROM t_service_spec " +
            "WHERE service_id = ? AND is_deleted = 0", serviceId);
        if (agg.isEmpty() || agg.get(0).get("p") == null) return;
        jdbc.update("UPDATE t_service_item SET price = ?, duration = ? WHERE id = ?",
            agg.get(0).get("p"), agg.get(0).get("d"), serviceId);
    }

    // ---- 参数清洗小工具 ----
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int intOr(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return def; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static BigDecimal money(Object o, BigDecimal def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return def;
        try { return new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (NumberFormatException e) { throw new BizException(ErrorCode.PARAM_ERROR, "金额格式不正确：" + s); }
    }

    private static String mode(Object o) {
        String s = o == null ? "instore" : String.valueOf(o).trim();
        return "onsite".equals(s) ? "onsite" : "instore";
    }

    private static String petType(Object o) {
        String s = o == null ? "all" : String.valueOf(o).trim();
        return ("dog".equals(s) || "cat".equals(s)) ? s : "all";
    }

    private static String petKind(Object o) {
        String s = o == null ? "all" : String.valueOf(o).trim();
        return ("dog".equals(s) || "cat".equals(s)) ? s : "all";
    }

    /** "09:00" / "09:00:00" -> "09:00:00"，非法回落默认值 */
    private static String timeOr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return def;
        if (s.length() == 5) s = s + ":00";
        try { java.time.LocalTime.parse(s); return s; } catch (Exception e) { return def; }
    }

    /** DB 时间列 -> "HH:mm" */
    private static String timeStr(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o);
        return s.length() >= 5 ? s.substring(0, 5) : def;
    }

    // ============ 排班 (store.business_hours 存 JSON) ============

    @GetMapping("/merchant/schedule")
    public ApiResp<Map<String, Object>> getSchedule() {
        merchantId();
        long storeId = storeId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT business_hours FROM t_store WHERE id = ?", storeId);
        if (rows.isEmpty()) return ApiResp.ok(Map.of("schedule", Map.of()));
        Object raw = rows.get(0).get("business_hours");
        if (raw == null) return ApiResp.ok(Map.of("schedule", Map.of()));
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> sched = m.readValue(raw.toString(), Map.class);
            return ApiResp.ok(Map.of("schedule", sched));
        } catch (Exception e) {
            return ApiResp.ok(Map.of("schedule", Map.of()));
        }
    }

    @PutMapping("/merchant/schedule")
    @Transactional
    public ApiResp<Void> saveSchedule(@RequestBody Map<String, Object> body) {
        long storeId = storeId();
        Object sched = body.get("schedule");
        if (sched == null) throw new BizException(ErrorCode.PARAM_ERROR, "schedule 必填");
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sched);
            jdbc.update("UPDATE t_store SET business_hours = ? WHERE id = ?", json, storeId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "排班 JSON 序列化失败");
        }
        return ApiResp.ok();
    }

    // ============ 相册 (store.photos 存 JSON 数组) ============

    @PutMapping("/merchant/store/photos")
    public ApiResp<Void> savePhotos(@RequestBody Map<String, Object> body) {
        long storeId = storeId();
        @SuppressWarnings("unchecked")
        List<String> photos = (List<String>) body.getOrDefault("photos", new ArrayList<>());
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photos);
            jdbc.update("UPDATE t_store SET photos = ? WHERE id = ?", json, storeId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "相册 JSON 序列化失败");
        }
        return ApiResp.ok();
    }

    // ============ 商家订单详情（按订单号查） ============

    @GetMapping("/merchant/order/{orderNo}")
    public ApiResp<Map<String, Object>> merchantOrderDetail(@PathVariable String orderNo) {
        long uid = merchantId();
        Map<String, Object> order;
        try {
        order = jdbc.queryForMap(
            "SELECT o.id, o.order_no, o.user_id, o.store_id, o.total_amount, o.pay_amount, o.freight_amount, o.status, " +
            "o.receiver_snapshot, o.remark, o.created_at, o.paid_at, o.delivered_at, " +
            "u.nickname AS buyer_name, u.phone AS buyer_phone, " +
            "rf.id AS refund_id, rf.reason AS refund_reason, rf.status AS refund_status " +
            "FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " +
            "LEFT JOIN t_refund rf ON rf.order_no = o.order_no AND rf.status = 'pending' AND rf.is_deleted = 0 " +
            "WHERE o.order_no = ? AND o.is_deleted = 0", orderNo);
        } catch (Exception e) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        long orderId = ((Number) order.get("id")).longValue();
        // 商品明细（用于展示 + 商品归属兜底）
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT oi.id, oi.product_id, oi.sku_id, oi.product_name, oi.product_image, oi.price, oi.quantity, " +
            "p.merchant_id FROM t_order_item oi LEFT JOIN t_product p ON p.id = oi.product_id " +
            "WHERE oi.order_id = ?", orderId);
        // 归属校验：优先按门店，兜底按商品商家
        boolean owned = false;
        Object sid = order.get("store_id");
        if (sid != null) {
            Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_store WHERE id = ? AND merchant_id = ?",
                Integer.class, ((Number) sid).longValue(), uid);
            if (c != null && c > 0) owned = true;
        }
        if (!owned) {
            for (Map<String, Object> it : items) {
                Object mid = it.get("merchant_id");
                if (mid != null && ((Number) mid).longValue() == uid) { owned = true; break; }
            }
        }
        // 越权拦截：不属于本商家的订单直接 403（避免 buyerPhone/receiver_snapshot 泄露）
        if (!owned) {
            throw new BizException(ErrorCode.NO_PERMISSION, "该订单不属于当前商家");
        }
        order.put("items", items);
        order.put("owned", true);
        return ApiResp.ok(order);
    }

    // ============ 客户管理：按 user_id 聚合订单数据 ============
    @GetMapping("/merchant/customers")
    public ApiResp<List<Map<String, Object>>> customers(@RequestParam(required = false) String keyword) {
        long uid = merchantId();
        StringBuilder sql = new StringBuilder(
            "SELECT o.user_id, u.nickname, u.avatar, u.phone, u.vip_level, " +
            "COUNT(o.id) AS orderCount, " +
            "COALESCE(SUM(o.pay_amount), 0) AS totalAmount, " +
            "MAX(o.created_at) AS lastOrderAt " +
            "FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id AND u.is_deleted = 0 " +
            "WHERE o.is_deleted = 0 AND o.store_id IN (SELECT id FROM t_store WHERE merchant_id = ?) " +
            "AND o.status IN ('paid','done','shipping') ");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.nickname LIKE ? OR u.phone LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" GROUP BY o.user_id ORDER BY totalAmount DESC LIMIT 200");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", r.get("user_id"));
            m.put("nickname", r.get("nickname"));
            m.put("avatar", r.get("avatar"));
            m.put("phone", r.get("phone"));
            m.put("vipLevel", r.get("vip_level"));
            m.put("orderCount", r.get("orderCount"));
            m.put("totalAmount", r.get("totalAmount"));
            m.put("lastOrderAt", r.get("lastOrderAt") == null ? null : r.get("lastOrderAt").toString());
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    // ============ 订单备注（商家内部） ============
    @PutMapping("/merchant/order/{orderNo}/remark")
    public ApiResp<Void> updateRemark(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR", "CS");
        long uid = merchantId();
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        if (remark != null && remark.length() > 500) remark = remark.substring(0, 500);
        // 权限校验：订单需属于本商家
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order WHERE order_no = ? AND store_id IN (SELECT id FROM t_store WHERE merchant_id = ?) AND is_deleted = 0",
            Integer.class, orderNo, uid);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.NO_PERMISSION, "该订单不属于当前商家");
        jdbc.update("UPDATE t_order SET remark = ? WHERE order_no = ?", remark, orderNo);
        audit.log(uid, TokenContext.requireUserId(), currentOperator(), TokenContext.requireSubRole(),
            "order", "remark", orderNo, "更新订单备注：" + (remark == null ? "清空" : remark));
        return ApiResp.ok();
    }

    // ============ 退款处理（商家侧售后） ============
    // 列出本商家的退款申请；status: pending(待处理) / handled(已处理:refunded+rejected) / 具体状态
    @GetMapping("/merchant/refunds")
    public ApiResp<Map<String, Object>> refunds(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long uid = merchantId();
        boolean handled = "handled".equals(status);
        StringBuilder where = new StringBuilder(
            "FROM t_refund r " +
            "JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id " +
            "LEFT JOIN t_user u ON r.user_id = u.id " +
            "WHERE s.merchant_id = ? AND r.is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (handled) {
            where.append("AND r.status IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if ("processing".equals(status)) {
            // 进行中：所有非终态
            where.append("AND r.status NOT IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if ("todo".equals(status)) {
            // 需要商家动作：待处理 + 买家已寄回待收货
            where.append("AND r.status IN ('pending','returned') ");
        } else if (!"all".equals(status)) {
            where.append("AND r.status = ? ");
            args.add(status);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, args.toArray());
        int offset = (Math.max(page, 1) - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.order_no, r.user_id, u.nickname AS userName, u.phone AS userPhone, " +
            "r.amount, r.refund_amount, r.reason, r.status, r.orig_status, r.handle_note, " +
            "r.created_at, r.handled_at, r.shipped_at, r.finished_at, " +
            "r.refund_type, r.evidence_images, r.logistics_no, r.logistics_company, r.return_address, " +
            "r.new_logistics_no, r.new_logistics_company, r.reshipped_at, " +
            "r.appeal_reason, r.appealed_at, r.arbitrate_result, r.arbitrate_note, " +
            "u.nickname AS buyer_name, u.phone AS buyer_phone, o.total_amount, o.pay_amount " +
            where + "ORDER BY r.id DESC LIMIT ? OFFSET ?",
            append(args, size, offset).toArray());
        for (Map<String, Object> r : rows) {
            String st = (String) r.get("status");
            r.put("typeText", com.petmate.refund.RefundStatus.typeText((String) r.get("refund_type")));
            r.put("statusText", com.petmate.refund.RefundStatus.statusText(st));
            r.put("isFinal", com.petmate.refund.RefundStatus.isFinal(st));
        }
        // 各状态计数（用于 Tab 角标）
        Map<String, Object> stat = jdbc.queryForMap(
            "SELECT " +
            "COALESCE(SUM(CASE WHEN r.status='pending' THEN 1 ELSE 0 END),0) AS pendingCount, " +
            "COALESCE(SUM(CASE WHEN r.status IN ('pending','returned') THEN 1 ELSE 0 END),0) AS todoCount, " +
            "COALESCE(SUM(CASE WHEN r.status='returned' THEN 1 ELSE 0 END),0) AS returnedCount, " +
            "COALESCE(SUM(CASE WHEN r.status='appealing' THEN 1 ELSE 0 END),0) AS appealingCount, " +
            "COALESCE(SUM(CASE WHEN r.status='refunded' THEN 1 ELSE 0 END),0) AS refundedCount, " +
            "COALESCE(SUM(CASE WHEN r.status='exchanged' THEN 1 ELSE 0 END),0) AS exchangedCount, " +
            "COALESCE(SUM(CASE WHEN r.status='rejected' THEN 1 ELSE 0 END),0) AS rejectedCount " +
            "FROM t_refund r JOIN t_order o ON r.order_no=o.order_no JOIN t_store s ON o.store_id=s.id " +
            "WHERE s.merchant_id = ? AND r.is_deleted = 0", uid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.putAll(stat);
        return ApiResp.ok(data);
    }

    /** 商家查看单张售后单的完整进度时间线 */
    @GetMapping("/merchant/refund/{id}/timeline")
    public ApiResp<List<Map<String, Object>>> refundTimeline(@PathVariable long id) {
        long uid = merchantId();
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_refund r JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id WHERE r.id = ? AND s.merchant_id = ?",
            Integer.class, id, uid);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.NO_PERMISSION, "售后单不存在");
        return ApiResp.ok(refundLog.timeline(id));
    }

    /**
     * 同意售后（仅 OWNER/OPERATOR）。三种类型走三条路：
     *  - 仅退款   ：恢复库存 + 订单置 refunded，一步到位
     *  - 退货退款 ：置 returning，并要求给出退货地址，等买家寄回
     *  - 换货     ：置 exchanging，同样要退货地址，买家寄回后商家重发
     * 支持商家核定实退金额（refundAmount），上限为下单实付 amount。
     */
    @PostMapping("/merchant/refund/{id}/approve")
    @Transactional
    public ApiResp<Void> approveRefund(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        String note = body == null ? null : (String) body.get("note");
        String returnAddress = body == null ? null : (String) body.get("returnAddress");
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT r.order_no, r.orig_status, r.refund_type, r.amount, r.user_id " +
            "FROM t_refund r JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id " +
            "WHERE r.id = ? AND r.status = 'pending' AND r.is_deleted = 0 AND s.merchant_id = ?",
            id, uid);
        if (rs.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "售后单不存在或已被处理");
        Map<String, Object> rf = rs.get(0);
        String orderNo = (String) rf.get("order_no");
        String refundType = (String) rf.get("refund_type");
        BigDecimal maxAmount = (BigDecimal) rf.get("amount");
        boolean isExchange = com.petmate.refund.RefundStatus.TYPE_EXCHANGE.equals(refundType);
        boolean needReturn = com.petmate.refund.RefundStatus.needReturn(refundType);

        // 商家可下调退款金额（部分退款），不得超过下单实付
        BigDecimal finalAmount = parseAmount(body == null ? null : body.get("refundAmount"), maxAmount);
        if (finalAmount.compareTo(maxAmount) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额不能超过订单实付 ¥" + maxAmount);
        }
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额不能为负数");
        }
        if (needReturn && (returnAddress == null || returnAddress.isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写退货收件地址，买家需要按此地址寄回");
        }

        String toStatus = com.petmate.refund.RefundStatus.afterApprove(refundType);
        String operator = currentOperator();
        String logNote;

        if (!needReturn) {
            // 仅退款：恢复库存 + 退回下单时消耗的优惠券/鱼干，并直接退款完成
            restoreStock(orderNo);
            restoreCouponAndPoints(orderNo);
            jdbc.update("UPDATE t_order SET status = 'refunded' WHERE order_no = ?", orderNo);
            jdbc.update("UPDATE t_refund SET status = ?, refund_amount = ?, handle_note = ?, " +
                    "handled_at = NOW(), finished_at = NOW(), updated_at = NOW() WHERE id = ?",
                toStatus, finalAmount, note, id);
            logNote = "商家同意退款 ¥" + finalAmount
                + (finalAmount.compareTo(maxAmount) < 0 ? "（部分退款，原 ¥" + maxAmount + "）" : "")
                + (note == null || note.isBlank() ? "" : "：" + note);
        } else {
            jdbc.update("UPDATE t_refund SET status = ?, refund_amount = ?, return_address = ?, " +
                    "handle_note = ?, handled_at = NOW(), updated_at = NOW() WHERE id = ?",
                toStatus, finalAmount, returnAddress, note, id);
            logNote = (isExchange ? "商家同意换货" : "商家同意退货，核定退款 ¥" + finalAmount)
                + "，请将商品寄回：" + returnAddress
                + (note == null || note.isBlank() ? "" : "（" + note + "）");
        }

        // 通知买家：售后申请已通过
        long buyerUid = ((Number) rf.get("user_id")).longValue();
        String refundMsg;
        if (!needReturn) {
            refundMsg = "您的订单 " + orderNo + " 退款 ¥" + finalAmount + " 已原路退回";
        } else if (isExchange) {
            refundMsg = "您的换货申请已通过，请将商品寄回：" + returnAddress;
        } else {
            refundMsg = "您的退货申请已通过，请将商品寄回：" + returnAddress;
        }
        notify.notifyUser(buyerUid, "refund", "售后申请已通过", refundMsg, orderNo);

        refundLog.logMerchant(id, orderNo, uid, operator,
            com.petmate.refund.RefundLogService.ACT_APPROVE, "pending", toStatus, logNote);
        audit.log(uid, TokenContext.requireUserId(), operator, TokenContext.requireSubRole(),
            "order", "refund_approve", orderNo, logNote);
        return ApiResp.ok();
    }

    /**
     * 确认收到买家寄回的商品（仅 OWNER/OPERATOR）。
     *  - 退货退款：恢复库存 + 订单置 refunded，售后完成
     *  - 换货    ：不退钱、不恢复库存（要重新发同款出去），停在 returned 等商家发货
     */
    @PostMapping("/merchant/refund/{id}/confirm")
    @Transactional
    public ApiResp<Void> confirmRefund(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        String note = body == null ? null : (String) body.get("note");
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT r.order_no, r.refund_type, r.status, r.refund_amount, r.amount, r.user_id " +
            "FROM t_refund r JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id " +
            "WHERE r.id = ? AND r.status IN ('returning','exchanging','returned') " +
            "AND r.is_deleted = 0 AND s.merchant_id = ?",
            id, uid);
        if (rs.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "售后单不存在或状态不可确认");
        Map<String, Object> rf = rs.get(0);
        String orderNo = (String) rf.get("order_no");
        String cur = (String) rf.get("status");
        String refundType = (String) rf.get("refund_type");
        String operator = currentOperator();

        if (com.petmate.refund.RefundStatus.TYPE_EXCHANGE.equals(refundType)) {
            // 换货：确认收货只是流转到「待重发」，钱和库存都不动
            jdbc.update("UPDATE t_refund SET status = ?, handle_note = ?, updated_at = NOW() WHERE id = ?",
                com.petmate.refund.RefundStatus.RETURNED, note, id);
            String logNote = "商家已收到退回商品，待重新发货" + (note == null || note.isBlank() ? "" : "：" + note);
            notify.notifyUser(((Number) rf.get("user_id")).longValue(), "refund", "换货进度更新",
                "商家已收到您的退回商品，将尽快为您重新发货", orderNo);
            refundLog.logMerchant(id, orderNo, uid, operator,
                com.petmate.refund.RefundLogService.ACT_CONFIRM, cur, com.petmate.refund.RefundStatus.RETURNED, logNote);
            audit.log(uid, TokenContext.requireUserId(), operator, TokenContext.requireSubRole(),
                "order", "refund_confirm", orderNo, logNote);
            return ApiResp.ok();
        }

        BigDecimal finalAmount = rf.get("refund_amount") == null
            ? (BigDecimal) rf.get("amount") : (BigDecimal) rf.get("refund_amount");
        restoreStock(orderNo);
        restoreCouponAndPoints(orderNo);
        jdbc.update("UPDATE t_order SET status = 'refunded' WHERE order_no = ?", orderNo);
        jdbc.update("UPDATE t_refund SET status = ?, handle_note = ?, handled_at = COALESCE(handled_at, NOW()), " +
                "finished_at = NOW(), updated_at = NOW() WHERE id = ?",
            com.petmate.refund.RefundStatus.REFUNDED, note, id);
        String logNote = "商家已确认收货，退款 ¥" + finalAmount + " 已原路退回"
            + (note == null || note.isBlank() ? "" : "：" + note);
        notify.notifyUser(((Number) rf.get("user_id")).longValue(), "refund", "退款成功",
            "商家已确认收货，订单 " + orderNo + " 的退款 ¥" + finalAmount + " 已原路退回", orderNo);
        refundLog.logMerchant(id, orderNo, uid, operator,
            com.petmate.refund.RefundLogService.ACT_CONFIRM, cur, com.petmate.refund.RefundStatus.REFUNDED, logNote);
        audit.log(uid, TokenContext.requireUserId(), operator, TokenContext.requireSubRole(),
            "order", "refund_confirm", orderNo, logNote);
        return ApiResp.ok();
    }

    /** 换货重新发货（仅 OWNER/OPERATOR）：收到退回商品后寄出新品 */
    @PostMapping("/merchant/refund/{id}/reship")
    @Transactional
    public ApiResp<Void> reshipExchange(@PathVariable long id, @RequestBody Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        String company = body == null ? null : (String) body.get("logisticsCompany");
        String no = body == null ? null : (String) body.get("logisticsNo");
        String note = body == null ? null : (String) body.get("note");
        if (company == null || company.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写物流公司");
        if (no == null || no.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写物流单号");
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT r.order_no, r.status FROM t_refund r JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id " +
            "WHERE r.id = ? AND r.refund_type = 'EXCHANGE' AND r.status = 'returned' " +
            "AND r.is_deleted = 0 AND s.merchant_id = ?", id, uid);
        if (rs.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "换货单不存在或状态不可重新发货");
        String orderNo = (String) rs.get(0).get("order_no");
        String operator = currentOperator();
        jdbc.update("UPDATE t_refund SET status = ?, new_logistics_company = ?, new_logistics_no = ?, " +
                "reshipped_at = NOW(), handle_note = ?, updated_at = NOW() WHERE id = ?",
            com.petmate.refund.RefundStatus.RESHIPPED, company, no, note, id);
        String logNote = "商家已重新发货：" + company + " " + no;
        refundLog.logMerchant(id, orderNo, uid, operator, com.petmate.refund.RefundLogService.ACT_RESHIP,
            com.petmate.refund.RefundStatus.RETURNED, com.petmate.refund.RefundStatus.RESHIPPED, logNote);
        audit.log(uid, TokenContext.requireUserId(), operator, TokenContext.requireSubRole(),
            "order", "refund_reship", orderNo, logNote);
        return ApiResp.ok();
    }

    /** 驳回售后：订单恢复原状态（仅 OWNER/OPERATOR），买家可申请平台介入 */
    @PostMapping("/merchant/refund/{id}/reject")
    @Transactional
    public ApiResp<Void> rejectRefund(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        String note = body == null ? null : (String) body.get("note");
        if (note == null || note.toString().isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写拒绝理由");
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT r.order_no, r.orig_status, r.status, r.user_id " +
            "FROM t_refund r JOIN t_order o ON r.order_no = o.order_no " +
            "JOIN t_store s ON o.store_id = s.id " +
            "WHERE r.id = ? AND r.status IN ('pending','returning','exchanging','returned') " +
            "AND r.is_deleted = 0 AND s.merchant_id = ?",
            id, uid);
        if (rs.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "售后单不存在或已被处理");
        Map<String, Object> rf = rs.get(0);
        String orderNo = (String) rf.get("order_no");
        String orig = (String) rf.get("orig_status");
        String cur = (String) rf.get("status");
        String operator = currentOperator();
        jdbc.update("UPDATE t_order SET status = ? WHERE order_no = ?", orig == null ? "paid" : orig, orderNo);
        jdbc.update("UPDATE t_refund SET status = ?, handle_note = ?, handled_at = COALESCE(handled_at, NOW()), " +
                "finished_at = NOW(), updated_at = NOW() WHERE id = ?",
            com.petmate.refund.RefundStatus.REJECTED, note, id);
        refundLog.logMerchant(id, orderNo, uid, operator, com.petmate.refund.RefundLogService.ACT_REJECT,
            cur, com.petmate.refund.RefundStatus.REJECTED, "商家驳回：" + note + "。如有异议可申请平台介入");
        audit.log(uid, TokenContext.requireUserId(), operator, TokenContext.requireSubRole(),
            "order", "refund_reject", orderNo, "拒绝售后：" + note);
        notify.notifyUser(((Number) rf.get("user_id")).longValue(), "refund", "售后申请被驳回",
            "您的售后申请被驳回：" + note + "。如有异议可在订单详情申请平台介入。", orderNo);
        return ApiResp.ok();
    }

    /** 恢复订单商品库存 */
    private void restoreStock(String orderNo) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT sku_id, quantity FROM t_order_item WHERE order_id = (SELECT id FROM t_order WHERE order_no = ?)",
            orderNo);
        for (Map<String, Object> it : items) {
            jdbc.update("UPDATE t_product_sku SET stock = stock + ? WHERE id = ?",
                ((Number) it.get("quantity")).intValue(), ((Number) it.get("sku_id")).longValue());
        }
    }

    /** 退回下单时消耗的优惠券 + 鱼干（退款完成时调用，避免资产蒸发） */
    private void restoreCouponAndPoints(String orderNo) {
        List<Map<String, Object>> o = jdbc.queryForList(
            "SELECT user_id, coupon_id, points_used FROM t_order WHERE order_no = ? AND is_deleted = 0", orderNo);
        if (o.isEmpty()) return;
        Map<String, Object> ord = o.get(0);
        long uid = ((Number) ord.get("user_id")).longValue();
        Object cid = ord.get("coupon_id");
        if (cid != null) {
            jdbc.update(
                // 合法状态只有 available / used / expired，回退必须写 available（写 'unused' 会让券消失）
                "UPDATE t_user_coupon SET status = 'available', used_at = NULL " +
                "WHERE user_id = ? AND coupon_id = ? AND status = 'used'",
                uid, ((Number) cid).longValue());
        }
        Object pu = ord.get("points_used");
        int pointsUsed = pu == null ? 0 : ((Number) pu).intValue();
        if (pointsUsed > 0) {
            pointsService.refund(uid, pointsUsed, "订单退款退回", null);
        }
    }

    /** 解析商家填写的退款金额，空值回落到默认值 */
    private static BigDecimal parseAmount(Object raw, BigDecimal fallback) {
        if (raw == null) return fallback;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return fallback;
        try {
            return new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额格式不正确");
        }
    }

    // ============ 操作审计日志 ============
    @GetMapping("/merchant/operation-logs")
    public ApiResp<Map<String, Object>> operationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String module) {
        long uid = merchantId();
        StringBuilder where = new StringBuilder("WHERE merchant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (module != null && !module.isEmpty()) { where.append("AND module = ? "); args.add(module); }
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_operation_log " + where, Long.class, args.toArray());
        int offset = (Math.max(page, 1) - 1) * size;
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT id, operator_name, role, module, action, target, detail, created_at " +
            "FROM t_operation_log " + where + "ORDER BY id DESC LIMIT ? OFFSET ?",
            append(args, size, offset).toArray());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return ApiResp.ok(data);
    }

    private List<Object> append(List<Object> base, Object... extra) {
        List<Object> out = new ArrayList<>(base);
        for (Object e : extra) out.add(e);
        return out;
    }
}
