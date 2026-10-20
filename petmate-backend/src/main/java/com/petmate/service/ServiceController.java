package com.petmate.service;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * C 端服务 / 门店入口（v13 行业化升级）。
 *  - /service/categories   标准分类树（一级 + 二级 + 数量）
 *  - /service/search       多维筛选 + 排序，返回起价 / 销量 / 评分 / 标签
 *  - /service/{id}         详情：规格档位、加购项、服务包含、预约须知、退改政策
 *  - /service/{id}/slots   指定日期的可约时段与余位（按营业时段切片 + 容量约束）
 *  - /service/{id}/reviews 服务评价（含评分分布）
 *  - /store/{id}           门店详情 + 服务项 + 门店评价
 */
@RestController
public class ServiceController {

    private final JdbcTemplate jdbc;

    public ServiceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // ========================================================================
    // 分类树
    // ========================================================================

    /** 标准分类树：固定 6 大类，附各类实际在售数量与二级子类数量。 */
    @GetMapping("/service/categories")
    public ApiResp<List<Map<String, Object>>> categories() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT category, IFNULL(sub_category,'') AS sub_category, COUNT(*) AS cnt " +
            "FROM t_service_item WHERE is_deleted = 0 AND status = 1 " +
            "GROUP BY category, sub_category");

        // category -> (sub -> cnt)
        Map<String, Map<String, Integer>> agg = new LinkedHashMap<>();
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String cat = String.valueOf(r.get("category"));
            String sub = String.valueOf(r.get("sub_category"));
            int cnt = ((Number) r.get("cnt")).intValue();
            agg.computeIfAbsent(cat, k -> new LinkedHashMap<>()).merge(sub, cnt, Integer::sum);
            total.merge(cat, cnt, Integer::sum);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        // 先按标准顺序输出
        for (String cat : ServiceCatalog.SUBS.keySet()) {
            int cnt = total.getOrDefault(cat, 0);
            if (cnt == 0) continue; // 无在售服务的分类不展示，避免用户点进空页
            out.add(buildCategoryNode(cat, cnt, agg.getOrDefault(cat, Map.of())));
        }
        // 兜底：历史脏数据里不在标准体系内的分类也带出来，便于运营发现
        for (Map.Entry<String, Integer> e : total.entrySet()) {
            if (!ServiceCatalog.SUBS.containsKey(e.getKey())) {
                out.add(buildCategoryNode(e.getKey(), e.getValue(), agg.getOrDefault(e.getKey(), Map.of())));
            }
        }
        return ApiResp.ok(out);
    }

    private Map<String, Object> buildCategoryNode(String cat, int cnt, Map<String, Integer> subCnt) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("category", cat);
        node.put("name", cat);
        node.put("cnt", cnt);
        node.put("count", cnt);
        node.put("icon", ServiceCatalog.icon(cat));
        node.put("priceUnit", ServiceCatalog.priceUnit(cat));

        List<Map<String, Object>> subs = new ArrayList<>();
        List<String> std = ServiceCatalog.SUBS.getOrDefault(cat, List.of());
        for (String s : std) {
            int c = subCnt.getOrDefault(s, 0);
            if (c > 0) subs.add(Map.of("name", s, "cnt", c, "count", c));
        }
        for (Map.Entry<String, Integer> e : subCnt.entrySet()) {
            if (!e.getKey().isEmpty() && !std.contains(e.getKey())) {
                subs.add(Map.of("name", e.getKey(), "cnt", e.getValue(), "count", e.getValue()));
            }
        }
        node.put("subs", subs);
        return node;
    }

    // ========================================================================
    // 搜索
    // ========================================================================

    /**
     * 服务搜索：支持关键词 / 一级二级分类 / 适用宠物 / 服务方式 / 价格上限 / 门店 + 4 种排序。
     */
    @GetMapping("/service/search")
    public ApiResp<PageResp<Map<String, Object>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subCategory,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String serviceMode,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 服务浏览对游客开放（与商品浏览一致），仅「预约下单」环节要求登录

        StringBuilder where = new StringBuilder("WHERE si.is_deleted = 0 AND si.status = 1 AND s.is_deleted = 0");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            // 名称 / 描述 / 标签 / 门店名 全字段模糊，避免"搜洗澡搜不到基础洗浴"这类断链
            where.append(" AND (si.name LIKE ? OR si.description LIKE ? OR si.tags LIKE ? OR s.name LIKE ?" +
                         " OR si.category LIKE ? OR si.sub_category LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            for (int i = 0; i < 6; i++) args.add(kw);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND si.category = ?");
            args.add(category);
        }
        if (subCategory != null && !subCategory.isBlank()) {
            where.append(" AND si.sub_category = ?");
            args.add(subCategory);
        }
        if (petType != null && !petType.isBlank() && !"all".equals(petType)) {
            // 选"犬"时，同时命中"通用"服务
            where.append(" AND si.pet_type IN (?, 'all')");
            args.add(petType);
        }
        if (serviceMode != null && !serviceMode.isBlank()) {
            where.append(" AND si.service_mode = ?");
            args.add(serviceMode);
        }
        if (priceMax != null && priceMax.compareTo(BigDecimal.ZERO) > 0) {
            where.append(" AND si.price <= ?");
            args.add(priceMax);
        }
        if (storeId != null) {
            where.append(" AND si.store_id = ?");
            args.add(storeId);
        }

        String orderBy = switch (sort == null ? "default" : sort) {
            case "sales"      -> " ORDER BY si.sales DESC, si.rating DESC, si.id DESC";
            case "price_asc"  -> " ORDER BY si.price ASC, si.sales DESC, si.id DESC";
            case "price_desc" -> " ORDER BY si.price DESC, si.sales DESC, si.id DESC";
            case "rating"     -> " ORDER BY si.rating DESC, si.review_count DESC, si.id DESC";
            // 综合：有销量的靠前，其次评分，最后新品
            default           -> " ORDER BY (si.sales * 2 + si.review_count) DESC, si.rating DESC, si.id DESC";
        };

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_service_item si JOIN t_store s ON si.store_id = s.id " + where,
            Long.class, args.toArray());

        String sql =
            "SELECT si.id, si.store_id, s.name AS store_name, s.address AS store_address, " +
            "       si.name, si.category, si.sub_category, si.service_mode, si.pet_type, " +
            "       si.price, si.original_price, si.duration, si.cover, si.description, " +
            "       si.sales, si.rating, si.review_count, si.tags, si.onsite_fee, " +
            "       (SELECT COUNT(*) FROM t_service_spec sp WHERE sp.service_id = si.id AND sp.is_deleted = 0) AS spec_cnt, " +
            "       (SELECT MAX(sp2.price) FROM t_service_spec sp2 WHERE sp2.service_id = si.id AND sp2.is_deleted = 0) AS max_price " +
            "FROM t_service_item si JOIN t_store s ON si.store_id = s.id " +
            where + orderBy + " LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(Math.max(0, (page - 1) * size));

        List<Map<String, Object>> rows = jdbc.queryForList(sql, pageArgs.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(toListItem(r));
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, page, size));
    }

    private Map<String, Object> toListItem(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        String category = (String) r.get("category");
        m.put("id", r.get("id"));
        m.put("storeId", r.get("store_id"));
        m.put("storeName", r.get("store_name"));
        m.put("storeAddress", r.get("store_address"));
        m.put("name", r.get("name"));
        m.put("category", category);
        m.put("subCategory", r.get("sub_category"));
        m.put("serviceMode", r.get("service_mode"));
        m.put("petType", r.get("pet_type"));
        m.put("price", r.get("price"));            // 起价（= 最低档位价）
        m.put("maxPrice", r.get("max_price"));
        m.put("originalPrice", r.get("original_price"));
        m.put("priceUnit", ServiceCatalog.priceUnit(category));
        m.put("duration", r.get("duration"));
        m.put("cover", r.get("cover"));
        m.put("description", r.get("description"));
        m.put("sales", r.get("sales"));
        m.put("rating", r.get("rating"));
        m.put("reviewCount", r.get("review_count"));
        m.put("onsiteFee", r.get("onsite_fee"));
        m.put("specCount", r.get("spec_cnt"));
        m.put("tags", splitTags(r.get("tags")));
        m.put("icon", ServiceCatalog.icon(category));
        return m;
    }

    private List<String> splitTags(Object raw) {
        if (raw == null) return List.of();
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String t : s.split("[,，]")) {
            String v = t.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** 多行文本（服务包含项 / 预约须知）拆成数组 */
    private List<String> splitLines(Object raw) {
        if (raw == null) return List.of();
        String s = String.valueOf(raw);
        if (s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String line : s.split("\\\\n|\\r?\\n")) {
            String v = line.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    // ========================================================================
    // 详情
    // ========================================================================

    /** 服务项详情：含规格档位、加购项、服务包含、预约须知、退改政策、评分概览 */
    @GetMapping("/service/{id}")
    public ApiResp<Map<String, Object>> serviceDetail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT si.*, s.name AS store_name, s.address AS store_address, s.type AS store_type, " +
            "       s.business_hours, s.photos AS store_photos, s.lng, s.lat, s.merchant_id " +
            "FROM t_service_item si JOIN t_store s ON si.store_id = s.id " +
            "WHERE si.id = ? AND si.is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "服务项不存在");
        Map<String, Object> r = rows.get(0);
        String category = (String) r.get("category");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.get("id"));
        out.put("name", r.get("name"));
        out.put("category", category);
        out.put("subCategory", r.get("sub_category"));
        out.put("serviceMode", r.get("service_mode"));
        out.put("petType", r.get("pet_type"));
        out.put("price", r.get("price"));
        out.put("originalPrice", r.get("original_price"));
        out.put("priceUnit", ServiceCatalog.priceUnit(category));
        out.put("dailyPriced", ServiceCatalog.isDailyPriced(category));
        out.put("onsiteFee", r.get("onsite_fee"));
        out.put("duration", r.get("duration"));
        out.put("cover", r.get("cover"));
        out.put("description", r.get("description"));
        out.put("status", r.get("status"));
        out.put("sales", r.get("sales"));
        out.put("rating", r.get("rating"));
        out.put("reviewCount", r.get("review_count"));
        out.put("tags", splitTags(r.get("tags")));
        out.put("includeItems", splitLines(r.get("include_items")));
        out.put("notices", splitLines(r.get("notice")));
        out.put("refundPolicy", r.get("refund_policy"));
        out.put("icon", ServiceCatalog.icon(category));
        out.put("advanceDays", r.get("advance_days"));
        out.put("openTime", r.get("open_time"));
        out.put("closeTime", r.get("close_time"));
        out.put("slotMinutes", r.get("slot_minutes"));
        out.put("slotCapacity", r.get("slot_capacity"));

        List<Map<String, Object>> specs = listSpecs(id);
        out.put("specs", specs);
        out.put("addons", listAddons(id));
        // 价格区间上限：直接由已查出的档位算，避免再打一次 DB
        BigDecimal maxPrice = null;
        for (Map<String, Object> sp : specs) {
            Object p = sp.get("price");
            if (p instanceof BigDecimal bd && (maxPrice == null || bd.compareTo(maxPrice) > 0)) maxPrice = bd;
        }
        out.put("maxPrice", maxPrice);

        Map<String, Object> store = new LinkedHashMap<>();
        store.put("id", r.get("store_id"));
        store.put("name", r.get("store_name"));
        store.put("address", r.get("store_address"));
        store.put("type", r.get("store_type"));
        store.put("businessHours", r.get("business_hours"));
        store.put("photos", r.get("store_photos"));
        store.put("lng", r.get("lng"));
        store.put("lat", r.get("lat"));
        out.put("store", store);

        return ApiResp.ok(out);
    }

    List<Map<String, Object>> listSpecs(long serviceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, pet_kind, coat, weight_min, weight_max, price, duration " +
            "FROM t_service_spec WHERE service_id = ? AND is_deleted = 0 ORDER BY sort_no, price", serviceId);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("petKind", r.get("pet_kind"));
            m.put("coat", r.get("coat"));
            m.put("weightMin", r.get("weight_min"));
            m.put("weightMax", r.get("weight_max"));
            m.put("price", r.get("price"));
            m.put("duration", r.get("duration"));
            out.add(m);
        }
        return out;
    }

    List<Map<String, Object>> listAddons(long serviceId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, price, duration, description " +
            "FROM t_service_addon WHERE service_id = ? AND is_deleted = 0 ORDER BY sort_no, id", serviceId);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("price", r.get("price"));
            m.put("duration", r.get("duration"));
            m.put("description", r.get("description"));
            out.add(m);
        }
        return out;
    }

    // ========================================================================
    // 可约时段
    // ========================================================================

    /**
     * 指定日期的可约时段。
     * 时段由 open_time / close_time / slot_minutes 切片而来，
     * 余位 = slot_capacity - 该时段已存在的有效预约数（pending + confirmed）。
     */
    @GetMapping("/service/{id}/slots")
    public ApiResp<Map<String, Object>> slots(
            @PathVariable long id,
            @RequestParam(required = false) String date) {
        // 时段查询同样对游客开放，便于未登录用户先看余位再决定注册

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT open_time, close_time, slot_minutes, slot_capacity, advance_days, duration, category " +
            "FROM t_service_item WHERE id = ? AND is_deleted = 0 AND status = 1", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "服务项不存在或已下架");
        Map<String, Object> cfg = rows.get(0);

        LocalDate day;
        try {
            day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式需为 yyyy-MM-dd");
        }
        int advanceDays = ((Number) cfg.get("advance_days")).intValue();
        LocalDate today = LocalDate.now();
        if (day.isBefore(today)) throw new BizException(ErrorCode.PARAM_ERROR, "不能预约过去的日期");
        if (day.isAfter(today.plusDays(advanceDays)))
            throw new BizException(ErrorCode.PARAM_ERROR, "最多可提前 " + advanceDays + " 天预约");

        LocalTime open = parseTime(String.valueOf(cfg.get("open_time")), LocalTime.of(9, 0));
        LocalTime close = parseTime(String.valueOf(cfg.get("close_time")), LocalTime.of(20, 0));
        int slotMinutes = Math.max(15, ((Number) cfg.get("slot_minutes")).intValue());
        int capacity = Math.max(1, ((Number) cfg.get("slot_capacity")).intValue());

        // 该日已被占用的时段计数
        Map<String, Integer> booked = new HashMap<>();
        List<Map<String, Object>> bk = jdbc.queryForList(
            "SELECT DATE_FORMAT(appointment_time, '%H:%i') AS hm, COUNT(*) AS cnt " +
            "FROM t_appointment WHERE service_id = ? AND DATE(appointment_time) = ? " +
            "AND is_deleted = 0 AND status IN ('pending','confirmed') GROUP BY hm",
            id, day.toString());
        for (Map<String, Object> b : bk) {
            booked.put(String.valueOf(b.get("hm")), ((Number) b.get("cnt")).intValue());
        }

        List<Map<String, Object>> slots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalTime cur = open;
        // 末尾不足一个完整时段则不再切；最多 48 段防御死循环
        while (!cur.plusMinutes(slotMinutes).isAfter(close) && slots.size() < 48) {
            String hm = cur.format(HHMM);
            int used = booked.getOrDefault(hm, 0);
            int left = Math.max(0, capacity - used);
            boolean past = day.equals(today) && LocalDateTime.of(day, cur).isBefore(now.plusMinutes(30));

            String end = cur.plusMinutes(slotMinutes).format(HHMM);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("time", hm);
            s.put("endTime", end);
            s.put("label", hm + "-" + end);   // 前端时段卡片直接展示
            s.put("capacity", capacity);
            s.put("booked", used);
            s.put("left", left);
            s.put("remain", left);            // 与前端 ServiceSlot.remain 契约对齐
            s.put("disabled", past || left <= 0);
            s.put("reason", past ? "已过时间" : (left <= 0 ? "已约满" : null));
            slots.add(s);
            cur = cur.plusMinutes(slotMinutes);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day.toString());
        out.put("openTime", open.format(HHMM));
        out.put("closeTime", close.format(HHMM));
        out.put("slotMinutes", slotMinutes);
        out.put("advanceDays", advanceDays);
        out.put("slots", slots);
        return ApiResp.ok(out);
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

    // ========================================================================
    // 评价
    // ========================================================================

    /** 服务评价列表 + 评分分布 */
    @GetMapping("/service/{id}/reviews")
    public ApiResp<Map<String, Object>> reviews(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_service_review WHERE service_id = ? AND is_deleted = 0", Long.class, id);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.rating, r.content, r.images, r.spec_name, r.reply, r.created_at, " +
            "       u.nickname, u.avatar " +
            "FROM t_service_review r LEFT JOIN t_user u ON r.user_id = u.id " +
            "WHERE r.service_id = ? AND r.is_deleted = 0 ORDER BY r.id DESC LIMIT ? OFFSET ?",
            id, size, Math.max(0, (page - 1) * size));

        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("rating", r.get("rating"));
            m.put("content", r.get("content"));
            m.put("images", splitTags(r.get("images")));
            m.put("specName", r.get("spec_name"));
            m.put("reply", r.get("reply"));
            String nick = maskNickname((String) r.get("nickname"));
            m.put("nickname", nick);          // 前端 ServiceReview.nickname
            m.put("avatar", r.get("avatar"));
            m.put("userNickname", nick);      // 兼容旧调用方
            m.put("userAvatar", r.get("avatar"));
            Object t = r.get("created_at");
            m.put("createdAt", t == null ? null : t.toString());
            list.add(m);
        }

        // 评分分布
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) dist.put(String.valueOf(i), 0);
        for (Map<String, Object> d : jdbc.queryForList(
                "SELECT rating, COUNT(*) AS cnt FROM t_service_review " +
                "WHERE service_id = ? AND is_deleted = 0 GROUP BY rating", id)) {
            dist.put(String.valueOf(((Number) d.get("rating")).intValue()), ((Number) d.get("cnt")).intValue());
        }

        Map<String, Object> summary = jdbc.queryForMap(
            "SELECT IFNULL(rating,0) AS rating, IFNULL(review_count,0) AS review_count, IFNULL(sales,0) AS sales " +
            "FROM t_service_item WHERE id = ?", id);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", total == null ? 0 : total);
        out.put("page", page);
        out.put("size", size);
        out.put("rating", summary.get("rating"));
        out.put("reviewCount", summary.get("review_count"));
        out.put("sales", summary.get("sales"));
        out.put("dist", dist);
        return ApiResp.ok(out);
    }

    private String maskNickname(String n) {
        if (n == null || n.isBlank()) return "匿名用户";
        if (n.length() <= 1) return n + "**";
        return n.charAt(0) + "**" + n.charAt(n.length() - 1);
    }

    // ========================================================================
    // 门店
    // ========================================================================

    /** 门店详情 + 服务项 + 门店评价 */
    @GetMapping("/store/{id}")
    public ApiResp<Map<String, Object>> storeDetail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, merchant_id, name, type, address, lng, lat, business_hours, photos, " +
            "       logo, description, audit_status " +
            "FROM t_store WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "门店不存在");
        Map<String, Object> s = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.get("id"));
        out.put("merchantId", s.get("merchant_id"));
        out.put("name", s.get("name"));
        out.put("type", s.get("type"));
        out.put("logo", s.get("logo"));
        out.put("description", s.get("description"));
        out.put("address", s.get("address"));
        out.put("lng", s.get("lng"));
        out.put("lat", s.get("lat"));
        out.put("businessHours", s.get("business_hours"));
        out.put("photos", s.get("photos"));
        out.put("auditStatus", s.get("audit_status"));

        List<Map<String, Object>> svcRows = jdbc.queryForList(
            "SELECT id, name, category, sub_category, price, duration, cover, description, " +
            "       sales, rating, review_count, service_mode " +
            "FROM t_service_item WHERE store_id = ? AND is_deleted = 0 AND status = 1 " +
            "ORDER BY sales DESC, id DESC", id);
        List<Map<String, Object>> services = new ArrayList<>(svcRows.size());
        for (Map<String, Object> r : svcRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("category", r.get("category"));
            m.put("subCategory", r.get("sub_category"));
            m.put("price", r.get("price"));
            m.put("priceUnit", ServiceCatalog.priceUnit((String) r.get("category")));
            m.put("duration", r.get("duration"));
            m.put("cover", r.get("cover"));
            m.put("description", r.get("description"));
            m.put("sales", r.get("sales"));
            m.put("rating", r.get("rating"));
            m.put("reviewCount", r.get("review_count"));
            m.put("serviceMode", r.get("service_mode"));
            services.add(m);
        }
        out.put("services", services);

        // 门店评价（聚合该店所有服务的评价）
        List<Map<String, Object>> rvRows = jdbc.queryForList(
            "SELECT r.id, r.rating, r.content, r.images, r.spec_name, r.reply, r.created_at, " +
            "       r.user_id, u.nickname, u.avatar, si.name AS service_name " +
            "FROM t_service_review r LEFT JOIN t_user u ON r.user_id = u.id " +
            "LEFT JOIN t_service_item si ON r.service_id = si.id " +
            "WHERE r.store_id = ? AND r.is_deleted = 0 ORDER BY r.id DESC LIMIT 20", id);
        List<Map<String, Object>> reviews = new ArrayList<>(rvRows.size());
        for (Map<String, Object> r : rvRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            String nick = maskNickname((String) r.get("nickname"));
            m.put("nickname", nick);          // 前端 ServiceReview.nickname
            m.put("avatar", r.get("avatar"));
            m.put("userNickname", nick);      // 兼容旧调用方
            m.put("userAvatar", r.get("avatar"));
            m.put("rating", r.get("rating"));
            m.put("content", r.get("content"));
            m.put("images", splitTags(r.get("images")));
            m.put("specName", r.get("spec_name"));
            m.put("serviceName", r.get("service_name"));
            m.put("reply", r.get("reply"));
            Object t = r.get("created_at");
            m.put("createdAt", t == null ? null : t.toString());
            reviews.add(m);
        }
        out.put("reviews", reviews);

        Double avg = jdbc.queryForObject(
            "SELECT IFNULL(AVG(rating), 0) FROM t_service_review WHERE store_id = ? AND is_deleted = 0",
            Double.class, id);
        out.put("averageRating", avg == null ? 0.0 : Math.round(avg * 10) / 10.0);
        Integer rc = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_service_review WHERE store_id = ? AND is_deleted = 0", Integer.class, id);
        out.put("reviewCount", rc == null ? 0 : rc);
        return ApiResp.ok(out);
    }
}
