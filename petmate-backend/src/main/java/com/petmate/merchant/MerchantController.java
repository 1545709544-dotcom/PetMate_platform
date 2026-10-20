package com.petmate.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import com.petmate.track.MetricService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
public class MerchantController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final MetricService metricService;
    private final com.petmate.common.AuditService audit;
    private final com.petmate.common.NotifyService notify;

    public MerchantController(JdbcTemplate jdbc, MetricService metricService,
                              com.petmate.common.AuditService audit, com.petmate.common.NotifyService notify) {
        this.jdbc = jdbc;
        this.metricService = metricService;
        this.audit = audit;
        this.notify = notify;
    }

    /** 当前操作人展示名（店主取昵称，员工取员工昵称） */
    private String currentOperator() {
        Long sid = TokenContext.getStaffId();
        if (sid != null) {
            String n = jdbc.queryForObject("SELECT nickname FROM t_merchant_staff WHERE id = ?", String.class, sid);
            return n != null ? n : "员工";
        }
        String n = jdbc.queryForObject("SELECT nickname FROM t_user WHERE id = ?", String.class, TokenContext.requireUserId());
        return n != null ? n : "店主";
    }

    @GetMapping("/merchant/dashboard")
    public ApiResp<Map<String, Object>> dashboard() {
        long uid = merchantId();
        // 通过 t_order_item → t_product.merchant_id 过滤当前商家商品订单
        Map<String, Object> today = jdbc.queryForMap(
            "SELECT COUNT(DISTINCT o.id) AS cnt, COALESCE(SUM(oi.price * oi.quantity),0) AS amt " +
            "FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND DATE(o.paid_at) = CURDATE()", uid);
        // 待处理：含 paid（待发货）+ pending_pay
        Integer pending = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT o.id) FROM t_order o " +
            "JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.status IN ('pending_pay','paid') AND o.is_deleted = 0",
            Integer.class, uid);
        // 新客户：本商家商品订单去重的买家数
        Integer newCust = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT o.user_id) FROM t_order o " +
            "JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0",
            Integer.class, uid);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("todayOrders", today.get("cnt"));
        r.put("todaySales", today.get("amt"));
        r.put("pendingOrders", pending);
        r.put("newCustomers", newCust);
        return ApiResp.ok(r);
    }

    @GetMapping("/merchant/data/sales")
    public ApiResp<List<Map<String, Object>>> sales(@RequestParam(defaultValue = "7d") String range) {
        int days = "30d".equals(range) ? 30 : "90d".equals(range) ? 90 : 7;
        long uid = merchantId();
        List<Map<String, Object>> rows;
        try {
            // 按门店归属聚合：订单 store_id 属于本商家，直接汇总 order_item，不依赖 t_product 是否存在
            rows = jdbc.queryForList(
                "SELECT DATE_FORMAT(o.created_at, '%Y-%m-%d') AS date, " +
                "COUNT(DISTINCT o.id) AS orderCount, COALESCE(SUM(oi.price * oi.quantity),0) AS salesAmount, " +
                "0 AS visitorCount, 0.0 AS conversionRate " +
                "FROM t_order o " +
                "JOIN t_order_item oi ON oi.order_id = o.id " +
                "WHERE o.store_id IN (SELECT id FROM t_store WHERE merchant_id = ?) AND o.is_deleted = 0 " +
                "  AND o.status IN ('paid','done','shipping') " +
                "  AND o.created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
                "GROUP BY DATE_FORMAT(o.created_at, '%Y-%m-%d') ORDER BY MIN(o.created_at)",
                uid, days);
        } catch (Exception ignore) {
            rows = new ArrayList<>();
        }
        if (rows.isEmpty()) {
            // 演示态无数据时不造假,返回空日期数组让前端显示 0
            java.time.LocalDate today = java.time.LocalDate.now();
            for (int i = days - 1; i >= 0; i--) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", today.minusDays(i).toString());
                m.put("orderCount", 0);
                m.put("salesAmount", 0.0);
                m.put("visitorCount", 0);
                m.put("conversionRate", 0.0);
                rows.add(m);
            }
        }
        return ApiResp.ok(rows);
    }

    @GetMapping("/merchant/data/visitor")
    public ApiResp<List<Map<String, Object>>> visitor(@RequestParam(defaultValue = "7d") String range) {
        // v2.0: 同 sales，按 merchant_id 过滤；visitorCount 留 0（依赖埋点）
        return sales(range);
    }

    @GetMapping("/merchant/data/convert")
    public ApiResp<Map<String, Object>> convert(@RequestParam(defaultValue = "7d") String range) {
        int days = "30d".equals(range) ? 30 : "90d".equals(range) ? 90 : 7;
        long uid = merchantId();
        // v2.0: 调 MetricService，按 merchant_id 真读或估算（petmate.metrics.real 控制）
        MetricService.FunnelStats fs = metricService.funnel(uid, days);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pv", fs.pv);
        r.put("uv", 0);             // uv 需要埋点 + distinct user,演示态先留 0
        r.put("cartCount", fs.addCart);
        r.put("orderCount", fs.order);
        r.put("paidCount", fs.paid);
        return ApiResp.ok(r);
    }

    @GetMapping("/merchant/store")
    public ApiResp<Map<String, Object>> store() {
        long uid = merchantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, merchant_id, name, type, address, lng, lat, business_hours, photos, logo, description, audit_status, " +
            "created_at, updated_at FROM t_store WHERE merchant_id = ? AND is_deleted = 0 ORDER BY id LIMIT 1", uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "门店不存在");
        Map<String, Object> s = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.get("id"));
        out.put("merchantId", s.get("merchant_id"));
        out.put("name", s.get("name"));
        out.put("type", s.get("type"));
        out.put("address", s.get("address"));
        out.put("lng", s.get("lng"));
        out.put("lat", s.get("lat"));
        out.put("businessHours", s.get("business_hours"));
        out.put("photos", s.get("photos"));
        out.put("logo", s.get("logo"));
        out.put("description", s.get("description"));
        out.put("auditStatus", s.get("audit_status"));
        out.put("createdAt", s.get("created_at"));
        out.put("updatedAt", s.get("updated_at"));
        // 附加聚合指标:商品 / 服务 / 评价 / 预约总数
        Long storeId = ((Number) s.get("id")).longValue();
        out.put("productCount", countOrZero(
            "SELECT COUNT(*) FROM t_product WHERE merchant_id = ? AND is_deleted = 0", uid));
        out.put("serviceCount", countOrZero(
            "SELECT COUNT(*) FROM t_service_item si JOIN t_store st ON si.store_id = st.id " +
            "WHERE st.id = ? AND si.is_deleted = 0", storeId));
        out.put("reviewCount", countOrZero(
            "SELECT COUNT(*) FROM t_product_review r JOIN t_product p ON r.product_id = p.id " +
            "WHERE p.merchant_id = ? AND r.is_deleted = 0", uid));
        out.put("appointmentCount", countOrZero(
            "SELECT COUNT(*) FROM t_appointment WHERE store_id = ? AND is_deleted = 0", storeId));
        return ApiResp.ok(out);
    }

    private long countOrZero(String sql, Object... args) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class, args);
            return n == null ? 0L : n;
        } catch (Exception ignore) {
            return 0L;
        }
    }

    @PutMapping("/merchant/store")
    public ApiResp<Map<String, Object>> updateStore(@RequestBody Map<String, Object> body) {
        long uid = merchantId();
        // 动态部分更新：仅更新页面允许编辑的字段，避免把经纬度/营业时间/相册等字段误清零（修复原全量覆盖 bug）
        String[] editable = { "name", "type", "address", "logo", "description" };
        StringBuilder sql = new StringBuilder("UPDATE t_store SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        for (String f : editable) {
            if (!body.containsKey(f)) continue;
            if (!first) sql.append(", ");
            sql.append(f).append(" = ?");
            Object v = body.get(f);
            args.add(v == null ? null : String.valueOf(v));
            first = false;
        }
        if (first) {
            // 没有任何可更新字段，直接返回当前门店
            return store();
        }
        sql.append(" WHERE merchant_id = ? AND is_deleted = 0");
        args.add(uid);
        jdbc.update(sql.toString(), args.toArray());
        return store();
    }

    @GetMapping("/merchant/review")
    public ApiResp<List<Map<String, Object>>> reviews() {
        long uid = merchantId();
        // 评价实际落在 t_product_review（与下单商品 t_product.merchant_id 关联），而非空表 t_review
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.order_id, r.user_id, r.user_nickname, r.user_avatar, " +
            "r.rating, r.content, r.images, r.reply, " +
            "DATE_FORMAT(r.replied_at, '%Y-%m-%d %H:%i:%s') AS replied_at, " +
            "DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s') AS created_at " +
            "FROM t_product_review r JOIN t_product p ON r.product_id = p.id " +
            "WHERE p.merchant_id = ? AND r.is_deleted = 0 ORDER BY r.id DESC", uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(normalizeReview(r));
        return ApiResp.ok(out);
    }

    @PostMapping("/merchant/review/{id}/reply")
    public ApiResp<Void> replyReview(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = merchantId();
        // 校验所有权：评价归属当前商家的商品
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review r JOIN t_product p ON r.product_id = p.id " +
            "WHERE r.id = ? AND p.merchant_id = ?", Integer.class, id, uid);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "评价不存在");
        jdbc.update("UPDATE t_product_review SET reply = ?, replied_at = NOW() WHERE id = ?",
                body.get("reply"), id);
        return ApiResp.ok();
    }

    /** 评价行 → 前端 camelCase（与 frontend merchant.ts Review 接口对齐） */
    private Map<String, Object> normalizeReview(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("orderId", r.get("order_id"));
        m.put("userId", r.get("user_id"));
        m.put("userNickname", r.get("user_nickname"));
        m.put("userAvatar", r.get("user_avatar"));
        m.put("rating", r.get("rating"));
        m.put("content", r.get("content"));
        m.put("images", parseImages(r.get("images")));
        m.put("reply", r.get("reply"));
        m.put("repliedAt", r.get("replied_at"));
        m.put("createdAt", r.get("created_at"));
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseImages(Object v) {
        if (v == null) return List.of();
        if (v instanceof List) return (List<String>) v;
        if (v instanceof String s && !s.isEmpty()) {
            try { return JSON.readValue(s, List.class); } catch (Exception e) { return List.of(); }
        }
        return List.of();
    }

    @GetMapping("/merchant/finance")
    public ApiResp<Map<String, Object>> finance() {
        long uid = merchantId();
        Map<String, Object> r = new LinkedHashMap<>();
        // 通过 t_order_item → t_product.merchant_id 过滤当前商家商品订单
        Double total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(oi.price * oi.quantity),0) " +
            "FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND o.status IN ('paid','done')",
            Double.class, uid);
        Double pending = jdbc.queryForObject(
            "SELECT COALESCE(SUM(oi.price * oi.quantity),0) " +
            "FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND o.status = 'pending_pay'",
            Double.class, uid);
        Double settled = jdbc.queryForObject(
            "SELECT COALESCE(SUM(oi.price * oi.quantity),0) " +
            "FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND o.status = 'done'",
            Double.class, uid);
        r.put("totalIncome", total == null ? 0 : total);
        r.put("pendingSettlement", pending == null ? 0 : pending);
        r.put("settled", settled == null ? 0 : settled);
        r.put("records", jdbc.queryForList(
            "SELECT o.id, DATE_FORMAT(o.created_at, '%Y-%m-%d') AS date, " +
            "SUM(oi.price * oi.quantity) AS amount, " +
            "CASE WHEN o.status = 'done' THEN 'settled' ELSE 'pending' END AS status " +
            "FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND o.status IN ('paid','done') " +
            "GROUP BY o.id, o.created_at, o.status ORDER BY o.id DESC LIMIT 20", uid));
        return ApiResp.ok(r);
    }

    @GetMapping("/merchant/order")
    public ApiResp<Map<String, Object>> orders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long uid = merchantId();
        // 通过门店归属过滤订单；左连用户取买家昵称/手机
        StringBuilder where = new StringBuilder(
            "WHERE o.store_id IN (SELECT id FROM t_store WHERE merchant_id = ?) AND o.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            where.append(" AND (o.order_no LIKE ? OR u.nickname LIKE ? OR u.phone LIKE ?");
            where.append(" OR EXISTS (SELECT 1 FROM t_order_item oi WHERE oi.order_id = o.id AND oi.product_name LIKE ?))");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.trim().isEmpty()) {
            StringBuilder in = new StringBuilder(" AND o.status IN (");
            boolean first = true;
            for (String s : status.split(",")) {
                s = s.trim();
                if (s.isEmpty()) continue;
                if (!first) in.append(",");
                in.append("?");
                first = false;
                args.add(s);
            }
            in.append(")");
            if (!first) where.append(in);
        }
        long total = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT o.id) FROM t_order o LEFT JOIN t_user u ON u.id = o.user_id " + where,
            Long.class, args.toArray());
        int offset = (Math.max(page, 1) - 1) * size;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT o.id, o.order_no, o.store_id, o.total_amount, o.pay_amount, o.status, o.created_at, o.user_id, " +
            "u.nickname AS buyer_name, u.phone AS buyer_phone, " +
            "(SELECT GROUP_CONCAT(DISTINCT oi.product_name SEPARATOR '、') " +
            " FROM t_order_item oi WHERE oi.order_id = o.id) AS product_summary, " +
            "(SELECT oi.product_image FROM t_order_item oi WHERE oi.order_id = o.id LIMIT 1) AS product_image " +
            "FROM t_order o " +
            "LEFT JOIN t_user u ON u.id = o.user_id " +
            where + " GROUP BY o.id ORDER BY o.id DESC LIMIT ? OFFSET ?",
            pageArgs.toArray());
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ApiResp.ok(result);
    }

    @GetMapping("/merchant/product")
    public ApiResp<List<Map<String, Object>>> products() {
        long uid = merchantId();
        return ApiResp.ok(jdbc.queryForList(
            "SELECT id, name, sale_price, status, sale_count, view_count FROM t_product " +
            "WHERE merchant_id = ? AND is_deleted = 0 ORDER BY id DESC", uid));
    }

    @GetMapping("/merchant/service")
    public ApiResp<List<Map<String, Object>>> services() {
        long uid = merchantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT si.id, si.name, si.category, si.sub_category, si.service_mode, si.pet_type, " +
            "si.price, si.original_price, si.duration, si.status, si.cover, si.description, " +
            "si.sales, si.rating, si.review_count, si.tags, si.slot_capacity, si.slot_minutes, " +
            "(SELECT COUNT(*) FROM t_appointment a WHERE a.service_id = si.id AND a.is_deleted = 0) AS booked_count, " +
            "(SELECT COUNT(*) FROM t_appointment a WHERE a.service_id = si.id AND a.is_deleted = 0 " +
            " AND a.status IN ('pending','confirmed')) AS pending_count, " +
            "(SELECT COUNT(*) FROM t_service_spec sp WHERE sp.service_id = si.id AND sp.is_deleted = 0) AS spec_count, " +
            "(SELECT COUNT(*) FROM t_service_addon ad WHERE ad.service_id = si.id AND ad.is_deleted = 0) AS addon_count, " +
            "(SELECT MAX(sp.price) FROM t_service_spec sp WHERE sp.service_id = si.id AND sp.is_deleted = 0) AS max_price " +
            "FROM t_service_item si JOIN t_store s ON si.store_id = s.id " +
            "WHERE s.merchant_id = ? AND si.is_deleted = 0 ORDER BY si.id DESC", uid);
        for (Map<String, Object> r : rows) {
            String c = r.get("category") == null ? "" : String.valueOf(r.get("category"));
            r.put("icon", com.petmate.service.ServiceCatalog.icon(c));
            r.put("priceUnit", com.petmate.service.ServiceCatalog.priceUnit(c));
            r.put("subCategory", r.get("sub_category"));
            r.put("serviceMode", r.get("service_mode"));
            r.put("petType", r.get("pet_type"));
            r.put("originalPrice", r.get("original_price"));
            r.put("reviewCount", r.get("review_count"));
            r.put("bookedCount", r.get("booked_count"));
            r.put("pendingCount", r.get("pending_count"));
            r.put("specCount", r.get("spec_count"));
            r.put("addonCount", r.get("addon_count"));
            r.put("maxPrice", r.get("max_price"));
            r.put("slotCapacity", r.get("slot_capacity"));
            r.put("slotMinutes", r.get("slot_minutes"));
        }
        return ApiResp.ok(rows);
    }

    // ================ 商品 CRUD ================

    /** 商品图片上传（本地选择文件，多图） */
    @PostMapping(value = "/merchant/product/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> uploadProductImage(@RequestParam("files") MultipartFile[] files,
                                                          HttpServletRequest request) throws IOException {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        if (files == null || files.length == 0) throw new BizException(ErrorCode.PARAM_ERROR, "未选择文件");
        if (files.length > 12) throw new BizException(ErrorCode.PARAM_ERROR, "最多上传 12 张");
        Path root = Paths.get(System.getProperty("user.dir"), "static", "uploads", "product");
        Files.createDirectories(root);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String ct = file.getContentType();
            if (ct == null || !(ct.equals("image/jpeg") || ct.equals("image/png") || ct.equals("image/webp")))
                throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp");
            if (file.getSize() > 5 * 1024 * 1024)
                throw new BizException(ErrorCode.PARAM_ERROR, "单张图片不能超过 5MB");
            String ext = ct.equals("image/png") ? ".png" : ct.equals("image/webp") ? ".webp" : ".jpg";
            String filename = "m" + uid + "_" + System.currentTimeMillis() + "_" + urls.size() + ext;
            file.transferTo(root.resolve(filename).toFile());
            urls.add(request.getContextPath() + "/static/uploads/product/" + filename);
        }
        if (urls.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "未选择有效文件");
        return ApiResp.ok(Map.of("urls", urls));
    }

    /** 把对象序列化为 JSON 字符串（用于 json 列存储） */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    /** 新增商品 */
    @PostMapping("/merchant/product")
    @Transactional
    public ApiResp<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        Object name = body.get("name");
        Object categoryId = body.get("categoryId");
        Object mainImage = body.get("mainImage");
        Object salePrice = body.get("salePrice");
        if (name == null || categoryId == null || mainImage == null || salePrice == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "name/categoryId/mainImage/salePrice 必填");
        }
        // P0 校验：分类必须存在、售价不可为负，防止脏数据写入（#23）
        long catId = ((Number) categoryId).longValue();
        Integer catCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_category WHERE id = ?", Integer.class, catId);
        if (catCnt == null || catCnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "分类不存在");
        java.math.BigDecimal sp = new java.math.BigDecimal(salePrice.toString());
        if (sp.compareTo(java.math.BigDecimal.ZERO) < 0) throw new BizException(ErrorCode.PARAM_ERROR, "售价不能为负");

        Object status = body.get("status");
        Object detail = body.get("detail");
        Object originalPrice = body.get("originalPrice");

        // 规格定义（可选）、详情图（可选）、副图（可选）-> json 列
        String specsJson = toJson(body.get("specs"));
        String detailImagesJson = toJson(body.get("detailImages"));
        String subImagesJson = toJson(body.get("subImages"));

        jdbc.update(
            "INSERT INTO t_product (name, category_id, merchant_id, main_image, sub_images, detail, status, " +
            "sale_price, original_price, specs, detail_images) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            name, ((Number) categoryId).longValue(), uid, mainImage, subImagesJson, detail,
            status == null ? 1 : ((Number) status).intValue(),
            new java.math.BigDecimal(salePrice.toString()),
            originalPrice == null ? null : new java.math.BigDecimal(originalPrice.toString()),
            specsJson, detailImagesJson);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // SKU：有规格则按规格建；无规格则建一个默认 SKU（保证商品可被加购，库存取传入 stock）
        Object skusObj = body.get("skus");
        List<?> skus = (skusObj instanceof List) ? (List<?>) skusObj : null;
        if (skus != null && !skus.isEmpty()) {
            int idx = 0;
            for (Object o : skus) {
                Map<?, ?> s = (Map<?, ?>) o;
                String specValuesJson = toJson(s.get("specValues"));
                java.math.BigDecimal skuPrice = s.get("price") == null ? sp : new java.math.BigDecimal(String.valueOf(s.get("price")));
                int skuStock = s.get("stock") == null ? 0 : ((Number) s.get("stock")).intValue();
                String skuImage = s.get("image") == null ? null : s.get("image").toString();
                String skuCode = "P" + id + "-" + (++idx);
                jdbc.update("INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image, is_deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0)", id, skuCode, specValuesJson, skuPrice, skuStock, skuImage);
            }
        } else {
            int stock = body.get("stock") != null ? ((Number) body.get("stock")).intValue() : 0;
            jdbc.update("INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0)", id, "P" + id, null,
                new java.math.BigDecimal(salePrice.toString()), stock, mainImage.toString());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("merchantId", uid);
        out.put("name", name);
        audit.log(uid, TokenContext.requireUserId(), currentOperator(), TokenContext.requireSubRole(),
            "product", "create", String.valueOf(id), "新增商品：" + name);
        return ApiResp.ok(out);
    }

    /** 编辑商品（校验归属） */
    @Transactional
    @PutMapping("/merchant/product/{id}")
    public ApiResp<Map<String, Object>> updateProduct(@PathVariable long id,
                                                      @RequestBody Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        Integer own = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND merchant_id = ? AND is_deleted = 0",
            Integer.class, id, uid);
        if (own == null || own == 0) throw new BizException(ErrorCode.PARAM_ERROR, "商品不存在或不属于当前商家");

        StringBuilder sql = new StringBuilder("UPDATE t_product SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (body.get("name") != null)          { sql.append(first ? "" : ", ").append("name = ?"); args.add(body.get("name")); first = false; }
        if (body.get("categoryId") != null)    { sql.append(first ? "" : ", ").append("category_id = ?"); args.add(((Number) body.get("categoryId")).longValue()); first = false; }
        if (body.get("mainImage") != null)     { sql.append(first ? "" : ", ").append("main_image = ?"); args.add(body.get("mainImage")); first = false; }
        if (body.get("subImages") != null)     { sql.append(first ? "" : ", ").append("sub_images = ?"); args.add(toJson(body.get("subImages"))); first = false; }
        if (body.get("detail") != null)        { sql.append(first ? "" : ", ").append("detail = ?"); args.add(body.get("detail")); first = false; }
        if (body.get("salePrice") != null)     { sql.append(first ? "" : ", ").append("sale_price = ?"); args.add(new java.math.BigDecimal(body.get("salePrice").toString())); first = false; }
        if (body.get("originalPrice") != null) { sql.append(first ? "" : ", ").append("original_price = ?"); args.add(new java.math.BigDecimal(body.get("originalPrice").toString())); first = false; }
        if (body.get("status") != null)        { sql.append(first ? "" : ", ").append("status = ?"); args.add(((Number) body.get("status")).intValue()); first = false; }
        if (body.get("specs") != null)         { sql.append(first ? "" : ", ").append("specs = ?"); args.add(toJson(body.get("specs"))); first = false; }
        if (body.get("detailImages") != null)  { sql.append(first ? "" : ", ").append("detail_images = ?"); args.add(toJson(body.get("detailImages"))); first = false; }
        if (!first) {
            sql.append(" WHERE id = ? AND merchant_id = ?");
            args.add(id);
            args.add(uid);
            jdbc.update(sql.toString(), args.toArray());
        }

        // SKU 处理：按 id 更新 / 无 id 则新增；提交列表之外的已有 SKU 软删（is_deleted=1），
        // 避免"删除规格后旧 SKU 仍在售 / 无规格互转留幽灵 SKU"的数据损坏问题（#10）。
        Object skusObj = body.get("skus");
        if (skusObj instanceof List) {
            List<?> skus = (List<?>) skusObj;
            Set<Long> submittedIds = new LinkedHashSet<>();
            for (Object o : skus) {
                Map<?, ?> s = (Map<?, ?>) o;
                String specValuesJson = toJson(s.get("specValues"));
                java.math.BigDecimal skuPrice = s.get("price") == null ? null : new java.math.BigDecimal(String.valueOf(s.get("price")));
                int skuStock = s.get("stock") == null ? 0 : ((Number) s.get("stock")).intValue();
                String skuImage = s.get("image") == null ? null : s.get("image").toString();
                Object skuIdObj = s.get("id");
                if (skuIdObj != null) {
                    long skuId = ((Number) skuIdObj).longValue();
                    submittedIds.add(skuId);
                    jdbc.update("UPDATE t_product_sku SET price = ?, stock = ?, image = ?, spec_values = ?, is_deleted = 0 " +
                        "WHERE id = ? AND product_id = ?", skuPrice, skuStock, skuImage, specValuesJson, skuId, id);
                } else {
                    String skuCode = "P" + id + "-" + System.currentTimeMillis() + "-" + Math.abs(s.hashCode());
                    jdbc.update("INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0)", id, skuCode, specValuesJson, skuPrice, skuStock, skuImage);
                }
            }
            // 软删未提交的 SKU（前端移除的规格不再在售）
            if (!submittedIds.isEmpty()) {
                String in = submittedIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                jdbc.update("UPDATE t_product_sku SET is_deleted = 1 WHERE product_id = ? AND id NOT IN (" + in + ") AND is_deleted = 0", id);
            }
        } else if (body.get("stock") != null) {
            // 无规格商品：维护一个默认（spec_values IS NULL）SKU，并软删历史遗留的 spec SKU
            int stock = ((Number) body.get("stock")).intValue();
            Object spObj = body.get("salePrice");
            java.math.BigDecimal defPrice = spObj != null ? new java.math.BigDecimal(spObj.toString())
                : jdbc.queryForObject("SELECT sale_price FROM t_product WHERE id = ?", java.math.BigDecimal.class, id);
            Object miObj = body.get("mainImage");
            String defImage = miObj != null ? miObj.toString()
                : jdbc.queryForObject("SELECT main_image FROM t_product WHERE id = ?", String.class, id);
            Integer defId = jdbc.queryForObject(
                "SELECT id FROM t_product_sku WHERE product_id = ? AND spec_values IS NULL AND is_deleted = 0 LIMIT 1",
                Integer.class, id);
            if (defId == null) {
                jdbc.update("INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image, is_deleted) " +
                    "VALUES (?, ?, NULL, ?, ?, ?, 0)", id, "P" + id, defPrice, stock, defImage);
            } else {
                jdbc.update("UPDATE t_product_sku SET stock = ?, price = ?, image = ?, is_deleted = 0 WHERE id = ?",
                    stock, defPrice, defImage, defId);
            }
            jdbc.update("UPDATE t_product_sku SET is_deleted = 1 WHERE product_id = ? AND spec_values IS NOT NULL AND is_deleted = 0", id);
        }

        audit.log(uid, TokenContext.requireUserId(), currentOperator(), TokenContext.requireSubRole(),
            "product", "update", String.valueOf(id), "编辑商品：" + body.getOrDefault("name", id));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    /** 软删商品（校验归属） */
    @DeleteMapping("/merchant/product/{id}")
    public ApiResp<Map<String, Object>> deleteProduct(@PathVariable long id) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        Integer own = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND merchant_id = ? AND is_deleted = 0",
            Integer.class, id, uid);
        if (own == null || own == 0) throw new BizException(ErrorCode.PARAM_ERROR, "商品不存在或不属于当前商家");
        jdbc.update("UPDATE t_product SET is_deleted = 1 WHERE id = ? AND merchant_id = ?", id, uid);
        return ApiResp.ok(Map.of("id", id, "isDeleted", true));
    }

    /** 上下架切换 */
    @PostMapping("/merchant/product/{id}/toggle-status")
    public ApiResp<Map<String, Object>> toggleStatus(@PathVariable long id) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        Integer own = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND merchant_id = ? AND is_deleted = 0",
            Integer.class, id, uid);
        if (own == null || own == 0) throw new BizException(ErrorCode.PARAM_ERROR, "商品不存在或不属于当前商家");

        Integer cur = jdbc.queryForObject(
            "SELECT status FROM t_product WHERE id = ?", Integer.class, id);
        int next = (cur == null || cur == 0) ? 1 : 0;
        jdbc.update("UPDATE t_product SET status = ? WHERE id = ? AND merchant_id = ?", next, id, uid);
        return ApiResp.ok(Map.of("id", id, "status", next));
    }

    /** 商家商品详情（编辑用）：归属校验 + 返回 status + 软删过滤的 SKU；不计浏览量（与 C 端详情区分，#14）。 */
    @GetMapping("/merchant/product/{id}")
    public ApiResp<Map<String, Object>> merchantProductDetail(@PathVariable long id) {
        long uid = merchantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.category_id, p.brand_id, p.main_image, p.sub_images, p.detail, " +
            "p.detail_images, p.status, p.sale_price, p.original_price, p.tags, p.specs " +
            "FROM t_product p WHERE p.id = ? AND p.merchant_id = ? AND p.is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "商品不存在或不属于当前商家");
        Map<String, Object> p = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.get("id"));
        out.put("name", p.get("name"));
        out.put("categoryId", p.get("category_id"));
        out.put("brandId", p.get("brand_id"));
        out.put("mainImage", p.get("main_image"));
        out.put("subImages", p.get("sub_images"));
        out.put("detail", p.get("detail"));
        out.put("detailImages", p.get("detail_images"));
        out.put("status", p.get("status"));
        out.put("salePrice", p.get("sale_price"));
        out.put("originalPrice", p.get("original_price"));
        out.put("tags", p.get("tags"));
        out.put("specs", p.get("specs"));
        List<Map<String, Object>> skus = jdbc.queryForList(
            "SELECT id, sku_code, spec_values, price, stock, image FROM t_product_sku " +
            "WHERE product_id = ? AND is_deleted = 0", id);
        out.put("skus", skus);
        return ApiResp.ok(out);
    }

    /** 幂等设值上下架（批量操作后端）：明确把商品置为 status（0/1），而非取反，避免混合选择时误伤（#9）。 */
    @PutMapping("/merchant/product/{id}/status")
    @Transactional
    public ApiResp<Map<String, Object>> setProductStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        TokenContext.requireSubRole("OWNER", "OPERATOR");
        long uid = merchantId();
        Integer own = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND merchant_id = ? AND is_deleted = 0",
            Integer.class, id, uid);
        if (own == null || own == 0) throw new BizException(ErrorCode.PARAM_ERROR, "商品不存在或不属于当前商家");
        int status = body.get("status") == null ? 1 : ((Number) body.get("status")).intValue();
        if (status != 0 && status != 1) throw new BizException(ErrorCode.PARAM_ERROR, "status 必须为 0 或 1");
        jdbc.update("UPDATE t_product SET status = ? WHERE id = ? AND merchant_id = ?", status, id, uid);
        return ApiResp.ok(Map.of("id", id, "status", status));
    }

    // ================ 发货 ================

    /**
     * 发货：t_order.status 'paid' -> 'shipping', 并设置 delivered_at = NOW()（示意发货时间）
     * 归属判定（按任务描述兜底策略）：
     *  1) 优先通过 t_order_item JOIN t_product 取 merchant_id，校验 == 当前商家
     *  2) 否则通过 t_order.store_id JOIN t_store.merchant_id 校验
     *  3) 两者都校验不到时直接 403 拒绝（避免代发不属于自己的订单）
     */
    @PostMapping("/merchant/order/{orderNo}/ship")
    public ApiResp<Map<String, Object>> ship(@PathVariable String orderNo) {
        TokenContext.requireSubRole("OWNER", "OPERATOR", "WAREHOUSE");
        long uid = merchantId();
        Map<String, Object> order = jdbc.queryForMap(
            "SELECT id, order_no, status, store_id FROM t_order WHERE order_no = ? AND is_deleted = 0", orderNo);
        long orderId = ((Number) order.get("id")).longValue();
        String status = (String) order.get("status");
        if (!"paid".equals(status)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅 paid 状态订单可发货");
        }

        boolean owned = false;
        // (1) 商品订单：通过 order_item -> product.merchant_id
        Integer itemMatch = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order_item oi JOIN t_product p ON oi.product_id = p.id " +
            "WHERE oi.order_id = ? AND p.merchant_id = ?", Integer.class, orderId, uid);
        if (itemMatch != null && itemMatch > 0) owned = true;

        // (2) 服务订单：通过 store.merchant_id
        if (!owned && order.get("store_id") != null) {
            Integer storeMatch = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_store WHERE id = ? AND merchant_id = ?",
                Integer.class, ((Number) order.get("store_id")).longValue(), uid);
            if (storeMatch != null && storeMatch > 0) owned = true;
        }

        // (3) 越权拦截：都不属于本商家则拒绝发货
        if (!owned) {
            throw new BizException(ErrorCode.NO_PERMISSION, "该订单不属于当前商家");
        }

        jdbc.update("UPDATE t_order SET status = 'shipping', delivered_at = NOW() WHERE id = ?", orderId);
        // 通知买家：订单已发货
        Long buyerUid = jdbc.queryForObject("SELECT user_id FROM t_order WHERE id = ?", Long.class, orderId);
        if (buyerUid != null) {
            notify.notifyUser(buyerUid, "order", "订单已发货",
                "商家已发货，订单 " + orderNo + " 正在配送中，请耐心等待~", orderNo);
        }
        audit.log(uid, TokenContext.requireUserId(), currentOperator(), TokenContext.requireSubRole(),
            "order", "ship", orderNo, "订单发货");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo);
        out.put("status", "shipping");
        return ApiResp.ok(out);
    }

    private long merchantId() {
        TokenContext.requireRoleAndActive("merchant");
        return TokenContext.requireMerchantId();
    }

    // ================ 数据看板扩展 ================

    /**
     * 7 天活跃热力图（按订单/预约生成；演示态无数据时返回 0 占比）
     * 数据来源：t_order + t_appointment（合并为「有效交互」）
     */
    @GetMapping("/merchant/data/heatmap")
    public ApiResp<List<Map<String, Object>>> heatmap() {
        long uid = merchantId();
        List<Long> storeIds = jdbc.query(
            "SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0",
            (rs, i) -> rs.getLong(1), uid);
        if (storeIds.isEmpty()) storeIds = List.of(0L);
        String in = storeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        long max = 1;
        List<long[]> buf = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate d = today.minusDays(i);
            String start = d.toString() + " 00:00:00";
            String end = d.toString() + " 23:59:59";
            long orderCnt = countOrZero(
                "SELECT COUNT(*) FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
                "JOIN t_product p ON p.id = oi.product_id " +
                "WHERE p.merchant_id = ? AND o.is_deleted = 0 AND o.created_at BETWEEN ? AND ?",
                uid, start, end);
            long apptCnt = countOrZero(
                "SELECT COUNT(*) FROM t_appointment WHERE store_id IN (" + in + ") " +
                "AND is_deleted = 0 AND appointment_time BETWEEN ? AND ?",
                start, end);
            long total = orderCnt + apptCnt;
            buf.add(new long[]{i, total});
            max = Math.max(max, total);
        }
        // 归一化为 0-100 百分比
        for (long[] row : buf) {
            java.time.LocalDate d = today.minusDays(row[0]);
            int pct = (int) Math.round((row[1] * 100.0) / max);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", String.format("%02d日", d.getDayOfMonth()));
            m.put("date", d.toString());
            m.put("value", pct);
            m.put("rawCount", row[1]);
            // 0 值给个最低 8% 让格子有底色
            if (pct == 0) m.put("value", 8);
            // 标记峰值
            m.put("peak", row[1] == max && max > 0);
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /**
     * 内容审核预警（演示态聚合：低分评价 + 待发货积压 + 申诉）
     * 数据来源：t_review 低分 / t_order 长时未发货 / t_store 审核状态
     */
    @GetMapping("/merchant/data/alerts")
    public ApiResp<List<Map<String, Object>>> alerts() {
        long uid = merchantId();
        List<Long> storeIds = jdbc.query(
            "SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0",
            (rs, i) -> rs.getLong(1), uid);
        if (storeIds.isEmpty()) storeIds = List.of(0L);
        String in = storeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

        List<Map<String, Object>> out = new ArrayList<>();
        // (1) 低分评价
        List<Map<String, Object>> low = jdbc.queryForList(
            "SELECT r.id, r.user_nickname AS user, r.content, r.rating " +
            "FROM t_product_review r JOIN t_product p ON r.product_id = p.id " +
            "WHERE p.merchant_id = ? " +
            "AND r.is_deleted = 0 AND r.rating <= 2 ORDER BY r.id DESC LIMIT 5",
            uid);
        for (Map<String, Object> r : low) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "review-" + r.get("id"));
            m.put("type", "低分评价");
            m.put("tag", "red");
            String content = String.valueOf(r.get("content"));
            m.put("content", content.length() > 24 ? content.substring(0, 24) + "…" : content);
            m.put("user", r.get("user"));
            m.put("views", "★ " + r.get("rating"));
            m.put("source", "review");
            out.add(m);
        }
        // (2) 长时间未发货订单 (paid > 24h 仍 shipping? 这里查 paid 未发货)
        List<Map<String, Object>> stuck = jdbc.queryForList(
            "SELECT o.id, o.order_no, o.user_id, o.created_at FROM t_order o " +
            "JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.status = 'paid' AND o.is_deleted = 0 " +
            "AND o.paid_at IS NOT NULL " +
            "AND TIMESTAMPDIFF(HOUR, o.paid_at, NOW()) >= 24 ORDER BY o.id DESC LIMIT 5",
            uid);
        for (Map<String, Object> o : stuck) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "order-" + o.get("id"));
            m.put("type", "超时未发货");
            m.put("tag", "gold");
            m.put("content", "订单 " + o.get("order_no") + " 已支付超过 24 小时未发货");
            m.put("user", "用户ID " + o.get("user_id"));
            m.put("views", String.valueOf(o.get("created_at")));
            m.put("source", "order");
            out.add(m);
        }
        // (3) 门店审核状态提示（仅在未通过时插入）
        List<Map<String, Object>> stores = jdbc.queryForList(
            "SELECT id, name, audit_status FROM t_store WHERE merchant_id = ? AND is_deleted = 0", uid);
        for (Map<String, Object> s : stores) {
            int st = ((Number) s.get("audit_status")).intValue();
            if (st == 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", "store-" + s.get("id"));
                m.put("type", "门店待审核");
                m.put("tag", "blue");
                m.put("content", "门店「" + s.get("name") + "」正在审核中");
                m.put("user", "平台");
                m.put("views", "待处理");
                m.put("source", "store");
                out.add(m);
            } else if (st == 2) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", "store-" + s.get("id"));
                m.put("type", "门店被驳回");
                m.put("tag", "red");
                m.put("content", "门店「" + s.get("name") + "」审核未通过,请检查资料后重新提交");
                m.put("user", "平台");
                m.put("views", "需处理");
                m.put("source", "store");
                out.add(m);
            }
        }
        return ApiResp.ok(out);
    }

    /**
     * 工作台待办：聚合"即将开始的预约 + 待回复评价 + 库存紧张 + 待发货"
     */
    @GetMapping("/merchant/todos")
    public ApiResp<List<Map<String, Object>>> todos() {
        long uid = merchantId();
        List<Long> storeIds = jdbc.query(
            "SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0",
            (rs, i) -> rs.getLong(1), uid);
        if (storeIds.isEmpty()) storeIds = List.of(0L);
        String in = storeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

        List<Map<String, Object>> out = new ArrayList<>();

        // (1) 2 小时内即将开始的预约
        List<Map<String, Object>> upcoming = jdbc.queryForList(
            "SELECT a.id, a.service_name, a.appointment_time, a.user_id, u.nickname, u.phone " +
            "FROM t_appointment a JOIN t_user u ON a.user_id = u.id " +
            "WHERE a.store_id IN (" + in + ") AND a.is_deleted = 0 " +
            "AND a.status = 'pending' " +
            "AND a.appointment_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 2 HOUR) " +
            "ORDER BY a.appointment_time LIMIT 5");
        for (Map<String, Object> a : upcoming) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "appt-" + a.get("id"));
            m.put("icon", "schedule");
            m.put("color", "orange");
            m.put("title", a.get("appointment_time") + " 有「" + a.get("service_name") + "」预约");
            m.put("desc", "客户：" + (a.get("nickname") == null ? "用户" + a.get("user_id") : a.get("nickname"))
                + " · " + (a.get("phone") == null ? "" : a.get("phone")));
            m.put("urgent", true);
            out.add(m);
        }

        // (2) 待回复评价（加括号修正 AND/OR 优先级，避免跨商家污染）
        long replyPending = countOrZero(
            "SELECT COUNT(*) FROM t_product_review r JOIN t_product p ON r.product_id = p.id " +
            "WHERE p.merchant_id = ? " +
            "AND r.is_deleted = 0 AND (r.reply IS NULL OR r.reply = '')",
            uid);
        if (replyPending > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "review-pending");
            m.put("icon", "forum");
            m.put("color", "blue");
            m.put("title", "有 " + replyPending + " 条未回复评价");
            m.put("desc", "建议在 24 小时内完成回复");
            m.put("urgent", false);
            out.add(m);
        }

        // (3) 待发货订单
        long pendingShip = countOrZero(
            "SELECT COUNT(DISTINCT o.id) FROM t_order o " +
            "JOIN t_order_item oi ON oi.order_id = o.id " +
            "JOIN t_product p ON p.id = oi.product_id " +
            "WHERE p.merchant_id = ? AND o.status = 'paid' AND o.is_deleted = 0", uid);
        if (pendingShip > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "order-ship");
            m.put("icon", "inventory_2");
            m.put("color", "gold");
            m.put("title", "有 " + pendingShip + " 笔订单待发货");
            m.put("desc", "点击进入订单管理 → 发货");
            m.put("urgent", true);
            out.add(m);
        }

        // (4) 库存紧张/缺货商品
        List<Map<String, Object>> lowStock = jdbc.queryForList(
            "SELECT id, name FROM t_product WHERE merchant_id = ? AND is_deleted = 0 " +
            "AND status = 1 LIMIT 20", uid);
        for (Map<String, Object> p : lowStock) {
            long pid = ((Number) p.get("id")).longValue();
            long stock = countOrZero("SELECT COALESCE(SUM(stock),0) FROM t_product_sku WHERE product_id = ?", pid);
            if (stock > 0 && stock <= 5) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", "stock-" + pid);
                m.put("icon", "inventory_2");
                m.put("color", "gold");
                m.put("title", "库存预警：" + p.get("name"));
                m.put("desc", "当前剩余：" + stock + " 件");
                m.put("urgent", true);
                out.add(m);
                if (out.size() >= 8) break;
            }
        }
        return ApiResp.ok(out);
    }

    /**
     * 商品库存聚合：每个 SPU 的 SKU 库存总和 + SKU 数 + 是否下架
     * 用于商品管理列表展示真实库存
     */
    @GetMapping("/merchant/product/stock")
    public ApiResp<List<Map<String, Object>>> productStock() {
        long uid = merchantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.sale_price, p.original_price, p.status, p.sale_count, p.view_count, " +
            "p.main_image, p.category_id, c.name AS category_name, " +
            "(SELECT COALESCE(SUM(stock),0) FROM t_product_sku WHERE product_id = p.id AND is_deleted = 0) AS total_stock, " +
            "(SELECT COUNT(*) FROM t_product_sku WHERE product_id = p.id AND is_deleted = 0) AS sku_count " +
            "FROM t_product p LEFT JOIN t_product_category c ON p.category_id = c.id " +
            "WHERE p.merchant_id = ? AND p.is_deleted = 0 ORDER BY p.id DESC", uid);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("salePrice", r.get("sale_price"));
            m.put("originalPrice", r.get("original_price"));
            m.put("status", r.get("status"));
            m.put("saleCount", r.get("sale_count"));
            m.put("viewCount", r.get("view_count"));
            m.put("mainImage", r.get("main_image"));
            m.put("categoryId", r.get("category_id"));
            m.put("categoryName", r.get("category_name"));
            m.put("totalStock", r.get("total_stock"));
            m.put("skuCount", r.get("sku_count"));
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /**
     * 财务结算扩展：完整账单列表（含状态 + 类型 + 备注）
     */
    @GetMapping("/merchant/finance/records")
    public ApiResp<List<Map<String, Object>>> financeRecords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        long uid = merchantId();
        StringBuilder where = new StringBuilder("WHERE o.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        // 走商家商品归属过滤
        where.append(" AND EXISTS (SELECT 1 FROM t_order_item oi JOIN t_product p ON oi.product_id = p.id " +
                "WHERE oi.order_id = o.id AND p.merchant_id = ?)");
        args.add(uid);
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            if ("pending".equalsIgnoreCase(status)) {
                where.append(" AND o.status IN ('paid','shipping')");
            } else if ("settled".equalsIgnoreCase(status)) {
                where.append(" AND o.status = 'done'");
            } else {
                where.append(" AND o.status = ?");
                args.add(status);
            }
        }
        String sql = "SELECT o.id, o.order_no, o.pay_amount, o.status, " +
                "DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS date, " +
                "DATE_FORMAT(o.paid_at, '%Y-%m-%d %H:%i') AS paid_at, " +
                "DATE_FORMAT(o.finished_at, '%Y-%m-%d') AS settled_at " +
                "FROM t_order o " + where + " ORDER BY o.id DESC LIMIT ?";
        args.add(Math.min(Math.max(limit, 1), 200));
        // JDBC 返回下划线列名，映射为驼峰以对齐前端 MerchantFinanceRecord 契约
        // （pay_amount→payAmount / order_no→orderNo / paid_at→paidAt / settled_at→settledAt）
        List<Map<String, Object>> raw = jdbc.queryForList(sql, args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("orderNo", row.get("order_no"));
            m.put("payAmount", row.get("pay_amount"));
            m.put("status", row.get("status"));
            m.put("date", row.get("date"));
            m.put("paidAt", row.get("paid_at"));
            m.put("settledAt", row.get("settled_at"));
            out.add(m);
        }
        return ApiResp.ok(out);
    }
}
