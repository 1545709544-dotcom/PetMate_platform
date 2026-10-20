package com.petmate.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理后台配置类端点：商品/分类/秒杀/优惠券/黑名单/角色权限。
 * 大部分演示态不新增表：黑名单与角色权限落到 t_system_config 以 JSON 存储。
 */
@RestController
public class AdminConfigController {

    private final JdbcTemplate jdbc;
    private final com.petmate.config.SystemConfigService sysConfig;
    private static final ObjectMapper JSON = new ObjectMapper();

    public AdminConfigController(JdbcTemplate jdbc, com.petmate.config.SystemConfigService sysConfig) {
        this.jdbc = jdbc;
        this.sysConfig = sysConfig;
    }

    private void adminOnly() { TokenContext.requireRoleAndActive("admin"); }

    // ============ 商品管理 ============

    @GetMapping("/admin/product/list")
    public ApiResp<PageResp<Map<String, Object>>> productList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        adminOnly();
        StringBuilder where = new StringBuilder("WHERE p.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND p.name LIKE ?");
            args.add("%" + keyword + "%");
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND p.status = ?");
            args.add(Integer.parseInt(status));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_product p " + where, Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.category_id, c.name AS categoryName, p.sale_price, p.original_price, " +
            "p.status, p.sale_count, p.view_count, p.main_image, " +
            "(SELECT COUNT(*) FROM t_product_sku s WHERE s.product_id = p.id AND s.is_deleted = 0) AS sku_count, " +
            "(SELECT COALESCE(SUM(stock),0) FROM t_product_sku s WHERE s.product_id = p.id AND s.is_deleted = 0) AS total_stock " +
            "FROM t_product p LEFT JOIN t_product_category c ON p.category_id = c.id " +
            where + " ORDER BY p.id DESC LIMIT ? OFFSET ?", listArgs.toArray());
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    @PostMapping("/admin/product/{id}/status")
    public ApiResp<Void> toggleProductStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        int status = ((Number) body.get("status")).intValue();
        jdbc.update("UPDATE t_product SET status = ? WHERE id = ?", status, id);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'product', ?, ?, '')",
                TokenContext.get().userId, TokenContext.get().role,
                status == 1 ? "PRODUCT_ON" : "PRODUCT_OFF", String.valueOf(id));
        return ApiResp.ok();
    }

    @DeleteMapping("/admin/product/{id}")
    @Transactional
    public ApiResp<Void> deleteProduct(@PathVariable long id) {
        adminOnly();
        // SPU 软删；关联 SKU 软删（保留历史订单/售后可回补库存），购物车失效项一并清理（#21）
        jdbc.update("UPDATE t_product SET is_deleted = 1 WHERE id = ?", id);
        jdbc.update("UPDATE t_product_sku SET is_deleted = 1 WHERE product_id = ? AND is_deleted = 0", id);
        jdbc.update("DELETE FROM t_cart WHERE product_id = ?", id);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'product', ?, ?, '')",
                TokenContext.get().userId, TokenContext.get().role, "PRODUCT_DELETE", String.valueOf(id));
        return ApiResp.ok();
    }

    // ============ 分类管理 ============

    @GetMapping("/admin/category/list")
    public ApiResp<List<Map<String, Object>>> categoryList() {
        adminOnly();
        return ApiResp.ok(jdbc.queryForList(
            "SELECT id, parent_id AS parentId, name, icon, level, sort FROM t_product_category ORDER BY parent_id, sort, id"));
    }

    @PostMapping("/admin/category")
    public ApiResp<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) {
        adminOnly();
        String name = (String) body.get("name");
        long parentId = body.get("parentId") == null ? 0L : ((Number) body.get("parentId")).longValue();
        String icon = (String) body.get("icon");
        int sort = body.get("sort") == null ? 0 : ((Number) body.get("sort")).intValue();
        if (name == null || name.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "name 必填");
        if (parentId < 0) throw new BizException(ErrorCode.PARAM_ERROR, "parentId 非法");
        // 由 parentId 反推 level = 父 level + 1（顶层=1）；父不存在或层级超限则拒绝（#22）
        int parentLevel = 0;
        if (parentId != 0) {
            Integer pl = jdbc.queryForObject(
                "SELECT level FROM t_product_category WHERE id = ?", Integer.class, parentId);
            if (pl == null) throw new BizException(ErrorCode.PARAM_ERROR, "父分类不存在");
            parentLevel = pl;
        }
        int level = parentLevel + 1;
        if (level > 3) throw new BizException(ErrorCode.PARAM_ERROR, "最多支持三级分类");
        jdbc.update("INSERT INTO t_product_category (parent_id, name, icon, level, sort) VALUES (?, ?, ?, ?, ?)",
            parentId, name, icon, level, sort);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("level", level);
        return ApiResp.ok(out);
    }

    @PutMapping("/admin/category/{id}")
    public ApiResp<Void> updateCategory(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        // 层级校验：不能自挂为父，父必须存在且新层级 <= 3（#22）
        long pid = body.get("parentId") == null ? 0L : ((Number) body.get("parentId")).longValue();
        if (pid == id) throw new BizException(ErrorCode.PARAM_ERROR, "不能将分类设为自身的父级");
        int level = 1;
        if (pid != 0) {
            Integer pl = jdbc.queryForObject("SELECT level FROM t_product_category WHERE id = ?", Integer.class, pid);
            if (pl == null) throw new BizException(ErrorCode.PARAM_ERROR, "父分类不存在");
            level = pl + 1;
            if (level > 3) throw new BizException(ErrorCode.PARAM_ERROR, "最多支持三级分类");
        }
        StringBuilder sql = new StringBuilder("UPDATE t_product_category SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (body.get("name") != null)      { sql.append(first ? "" : ", ").append("name = ?"); args.add(body.get("name")); first = false; }
        if (body.get("parentId") != null)  { sql.append(first ? "" : ", ").append("parent_id = ?"); args.add(((Number) body.get("parentId")).longValue()); sql.append(", level = ?"); args.add(level); first = false; }
        if (body.get("icon") != null)      { sql.append(first ? "" : ", ").append("icon = ?"); args.add(body.get("icon")); first = false; }
        if (body.get("sort") != null)      { sql.append(first ? "" : ", ").append("sort = ?"); args.add(((Number) body.get("sort")).intValue()); first = false; }
        if (first) throw new BizException(ErrorCode.PARAM_ERROR, "无可更新字段");
        sql.append(" WHERE id = ?");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    @DeleteMapping("/admin/category/{id}")
    public ApiResp<Void> deleteCategory(@PathVariable long id) {
        adminOnly();
        // 有子分类或商品不可删
        Integer child = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_category WHERE parent_id = ? AND id <> ?", Integer.class, id, id);
        if (child != null && child > 0) throw new BizException(ErrorCode.PARAM_ERROR, "存在子分类，无法删除");
        Integer used = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE category_id = ? AND is_deleted = 0", Integer.class, id);
        if (used != null && used > 0) throw new BizException(ErrorCode.PARAM_ERROR, "该分类下仍有商品，无法删除");
        jdbc.update("DELETE FROM t_product_category WHERE id = ?", id);
        return ApiResp.ok();
    }

    // ============ 秒杀管理 ============

    @GetMapping("/admin/seckill/list")
    public ApiResp<List<Map<String, Object>>> seckillList() {
        adminOnly();
        return ApiResp.ok(jdbc.queryForList(
            "SELECT s.id, s.product_id, p.name AS product_name, s.title, s.seckill_price, s.stock, s.sold, " +
            "s.start_time, s.end_time, s.status FROM t_seckill s " +
            "JOIN t_product p ON s.product_id = p.id WHERE s.is_deleted = 0 ORDER BY s.id DESC"));
    }

    @PostMapping("/admin/seckill")
    public ApiResp<Map<String, Object>> createSeckill(@RequestBody Map<String, Object> body) {
        adminOnly();
        long productId = ((Number) body.get("productId")).longValue();
        String title = (String) body.get("title");
        BigDecimal price = new BigDecimal(body.get("seckillPrice").toString());
        int stock = ((Number) body.get("stock")).intValue();
        LocalDateTime start = LocalDateTime.parse(body.get("startTime").toString().replace(" ", "T"));
        LocalDateTime end = LocalDateTime.parse(body.get("endTime").toString().replace(" ", "T"));
        jdbc.update(
            "INSERT INTO t_seckill (product_id, title, seckill_price, stock, sold, start_time, end_time, status) " +
            "VALUES (?, ?, ?, ?, 0, ?, ?, 1)",
            productId, title, price, stock,
            Timestamp.valueOf(start), Timestamp.valueOf(end));
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    @PutMapping("/admin/seckill/{id}")
    public ApiResp<Void> updateSeckill(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        StringBuilder sql = new StringBuilder("UPDATE t_seckill SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (body.get("title") != null)         { sql.append(first ? "" : ", ").append("title = ?"); args.add(body.get("title")); first = false; }
        if (body.get("seckillPrice") != null)  { sql.append(first ? "" : ", ").append("seckill_price = ?"); args.add(new BigDecimal(body.get("seckillPrice").toString())); first = false; }
        if (body.get("stock") != null)         { sql.append(first ? "" : ", ").append("stock = ?"); args.add(((Number) body.get("stock")).intValue()); first = false; }
        if (body.get("startTime") != null)     { sql.append(first ? "" : ", ").append("start_time = ?"); args.add(Timestamp.valueOf(LocalDateTime.parse(body.get("startTime").toString().replace(" ", "T")))); first = false; }
        if (body.get("endTime") != null)       { sql.append(first ? "" : ", ").append("end_time = ?"); args.add(Timestamp.valueOf(LocalDateTime.parse(body.get("endTime").toString().replace(" ", "T")))); first = false; }
        if (body.get("status") != null)        { sql.append(first ? "" : ", ").append("status = ?"); args.add(((Number) body.get("status")).intValue()); first = false; }
        if (first) throw new BizException(ErrorCode.PARAM_ERROR, "无可更新字段");
        sql.append(" WHERE id = ?");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    @PostMapping("/admin/seckill/{id}/toggle")
    public ApiResp<Void> toggleSeckill(@PathVariable long id) {
        adminOnly();
        Integer cur = jdbc.queryForObject("SELECT status FROM t_seckill WHERE id = ?", Integer.class, id);
        int next = (cur == null || cur == 0) ? 1 : 0;
        jdbc.update("UPDATE t_seckill SET status = ? WHERE id = ?", next, id);
        return ApiResp.ok();
    }

    @DeleteMapping("/admin/seckill/{id}")
    public ApiResp<Void> deleteSeckill(@PathVariable long id) {
        adminOnly();
        jdbc.update("UPDATE t_seckill SET is_deleted = 1 WHERE id = ?", id);
        return ApiResp.ok();
    }

    // ============ 优惠券管理 ============

    @GetMapping("/admin/coupon/list")
    public ApiResp<List<Map<String, Object>>> couponList() {
        adminOnly();
        return ApiResp.ok(jdbc.queryForList(
            "SELECT id, name, type, threshold, amount, discount, valid_start, valid_end, status " +
            "FROM t_coupon WHERE is_deleted = 0 ORDER BY id DESC"));
    }

    @PostMapping("/admin/coupon")
    public ApiResp<Map<String, Object>> createCoupon(@RequestBody Map<String, Object> body) {
        adminOnly();
        String name = (String) body.get("name");
        String type = (String) body.get("type");
        BigDecimal threshold = new BigDecimal(body.getOrDefault("threshold", 0).toString());
        BigDecimal amount = new BigDecimal(body.getOrDefault("amount", 0).toString());
        Object discountObj = body.get("discount");
        BigDecimal discount = discountObj == null ? null : new BigDecimal(discountObj.toString());
        LocalDateTime start = LocalDateTime.parse(body.get("validStart").toString().replace(" ", "T"));
        LocalDateTime end = LocalDateTime.parse(body.get("validEnd").toString().replace(" ", "T"));
        jdbc.update(
            "INSERT INTO t_coupon (name, type, threshold, amount, discount, valid_start, valid_end, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
            name, type, threshold, amount, discount, Timestamp.valueOf(start), Timestamp.valueOf(end));
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    @PutMapping("/admin/coupon/{id}")
    public ApiResp<Void> updateCoupon(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        StringBuilder sql = new StringBuilder("UPDATE t_coupon SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (body.get("name") != null)         { sql.append(first ? "" : ", ").append("name = ?"); args.add(body.get("name")); first = false; }
        if (body.get("type") != null)         { sql.append(first ? "" : ", ").append("type = ?"); args.add(body.get("type")); first = false; }
        if (body.get("threshold") != null)    { sql.append(first ? "" : ", ").append("threshold = ?"); args.add(new BigDecimal(body.get("threshold").toString())); first = false; }
        if (body.get("amount") != null)       { sql.append(first ? "" : ", ").append("amount = ?"); args.add(new BigDecimal(body.get("amount").toString())); first = false; }
        if (body.get("discount") != null)     { sql.append(first ? "" : ", ").append("discount = ?"); args.add(new BigDecimal(body.get("discount").toString())); first = false; }
        if (body.get("validStart") != null)   { sql.append(first ? "" : ", ").append("valid_start = ?"); args.add(Timestamp.valueOf(LocalDateTime.parse(body.get("validStart").toString().replace(" ", "T")))); first = false; }
        if (body.get("validEnd") != null)     { sql.append(first ? "" : ", ").append("valid_end = ?"); args.add(Timestamp.valueOf(LocalDateTime.parse(body.get("validEnd").toString().replace(" ", "T")))); first = false; }
        if (body.get("status") != null)       { sql.append(first ? "" : ", ").append("status = ?"); args.add(((Number) body.get("status")).intValue()); first = false; }
        if (first) throw new BizException(ErrorCode.PARAM_ERROR, "无可更新字段");
        sql.append(" WHERE id = ?");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    @DeleteMapping("/admin/coupon/{id}")
    public ApiResp<Void> deleteCoupon(@PathVariable long id) {
        adminOnly();
        jdbc.update("UPDATE t_coupon SET is_deleted = 1 WHERE id = ?", id);
        return ApiResp.ok();
    }

    // ============ 紧急熔断（emergency_break, t_system_config.risk.emergency_break = "0"|"1"） ============

    @GetMapping("/admin/risk/emergency-break")
    public ApiResp<Map<String, Object>> getEmergencyBreak() {
        adminOnly();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", sysConfig.isEmergencyBreak());
        r.put("updatedBy", sysConfig.get("risk.emergency_break.by", ""));
        r.put("updatedAt", sysConfig.get("risk.emergency_break.at", ""));
        return ApiResp.ok(r);
    }

    @PutMapping("/admin/risk/emergency-break")
    public ApiResp<Map<String, Object>> setEmergencyBreak(@RequestBody Map<String, Object> body) {
        adminOnly();
        boolean on = Boolean.TRUE.equals(body.get("enabled"));
        long uid = TokenContext.get().userId;
        String ts = LocalDateTime.now().toString();
        writeConfig("risk.emergency_break", on ? "1" : "0");
        writeConfig("risk.emergency_break.by", String.valueOf(uid));
        writeConfig("risk.emergency_break.at", ts);
        // 写入后立即刷新内存缓存 —— 一处开关、全局生效，无需重启
        sysConfig.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'risk', ?, ?, '')",
                uid, TokenContext.get().role, on ? "EMERGENCY_BREAK_ON" : "EMERGENCY_BREAK_OFF", "");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", on);
        r.put("updatedBy", String.valueOf(uid));
        r.put("updatedAt", ts);
        return ApiResp.ok(r);
    }

    // ============ 系统参数配置（通用 key-value 管理，t_system_config） ============
    @GetMapping("/admin/system-config")
    public ApiResp<List<Map<String, Object>>> systemConfigList() {
        adminOnly();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT config_key, config_value, updated_at FROM t_system_config ORDER BY config_key");
        return ApiResp.ok(rows);
    }

    @PutMapping("/admin/system-config")
    public ApiResp<Void> updateSystemConfig(@RequestBody Map<String, Object> body) {
        adminOnly();
        if (body == null || body.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "配置内容为空");
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            if (key == null || key.isBlank()) continue;
            Object val = e.getValue();
            writeConfig(key, val == null ? "" : String.valueOf(val));
        }
        // 立即刷新内存缓存，全局生效（无需重启）
        sysConfig.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'config', 'UPDATE_CONFIG', '', '')",
                TokenContext.get().userId, TokenContext.get().role);
        return ApiResp.ok();
    }

    // 注: readConfig / writeConfig 见本类底部 "内部" 段

    // ============ 角色 / 权限（演示：t_system_config 存 JSON） ============
    // 权限树：按平台真实模块分组（菜单/按钮/API 合一，* 通配整模块）。
    // 这是 RBAC 的"权限目录"数据源，前端在"权限分配"页渲染为多模块勾选树。

    private static final List<Map<String, Object>> PERM_TREE = buildPermTree();

    private static List<Map<String, Object>> buildPermTree() {
        List<Map<String, Object>> tree = new ArrayList<>();
        tree.add(module("user", "用户管理", List.of(
                perm("user.list", "用户查看"), perm("user.ban", "封禁/解封"),
                perm("user.edit", "编辑用户"), perm("user.create", "新增用户"),
                perm("user.export", "导出用户"))));
        tree.add(module("merchant", "商家管理", List.of(
                perm("merchant.audit", "入驻审核"), perm("merchant.list", "商家管理"),
                perm("merchant.ban", "商家启停/解除"), perm("merchant.edit", "编辑商家"))));
        tree.add(module("post", "内容社区", List.of(
                perm("post.audit", "动态审核"), perm("report.handle", "举报处理"),
                perm("adoption.audit", "领养审核"))));
        tree.add(module("product", "商品管理", List.of(
                perm("product.list", "商品查看"), perm("product.edit", "编辑商品"),
                perm("product.status", "上/下架"), perm("product.delete", "删除商品"))));
        tree.add(module("order", "订单管理", List.of(
                perm("order.list", "订单查看"), perm("refund.list", "退款列表"),
                perm("refund.handle", "退款处理"))));
        tree.add(module("marketing", "营销管理", List.of(
                perm("marketing.seckill", "秒杀管理"), perm("marketing.coupon", "优惠券管理"))));
        tree.add(module("risk", "风控管理", List.of(
                perm("risk.sensitive", "敏感词管理"), perm("risk.emergency", "紧急熔断"))));
        tree.add(module("system", "系统管理", List.of(
                perm("role.manage", "角色权限"), perm("config.view", "系统配置"),
                perm("log.view", "操作日志"), perm("notify.push", "消息推送"))));
        return tree;
    }

    private static Map<String, Object> module(String key, String label, List<Map<String, Object>> perms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("module", key);
        m.put("label", label);
        m.put("perms", perms);
        return m;
    }

    private static Map<String, Object> perm(String key, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        return m;
    }

    /** 扁平化权限目录（兼容旧前端调用） */
    private static List<String> permCatalog() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> mod : PERM_TREE) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> perms = (List<Map<String, Object>>) mod.get("perms");
            for (Map<String, Object> p : perms) out.add((String) p.get("key"));
        }
        return out;
    }

    private List<Map<String, Object>> defaultRoles() {
        return List.of(
            role("admin", "系统管理员", "拥有平台全部权限的超级管理员，内置不可删除", true, List.of("*")),
            role("ops", "运营人员", "负责内容审核、商品与营销运营", false,
                 List.of("user.list", "post.audit", "product.list", "product.status", "marketing.*", "order.list")),
            role("support", "客服支持", "处理用户咨询、订单与退款相关事务", false,
                 List.of("user.list", "order.list", "refund.list")),
            role("finance", "财务管理", "负责订单与退款财务核对", false,
                 List.of("order.list", "refund.list", "refund.handle"))
        );
    }

    private Map<String, Object> role(String id, String name, String desc, boolean builtin, List<String> perms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", desc);
        m.put("builtin", builtin);
        m.put("perms", perms);
        return m;
    }

    @GetMapping("/admin/roles")
    public ApiResp<Map<String, Object>> roles() {
        adminOnly();
        String raw = readConfig("admin.roles", "[]");
        List<Map<String, Object>> roles = parseList(raw);
        if (roles.isEmpty()) {
            roles = defaultRoles();
        } else {
            // 规范化 + 补全：保证 admin 始终内置且拥有 *；缺失的默认角色自动补回（幂等，不覆盖已有自定义角色）
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            for (Map<String, Object> r : roles) byId.put((String) r.get("id"), r);
            Map<String, Object> admin = byId.get("admin");
            if (admin != null) {
                admin.put("builtin", true);
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) admin.get("perms");
                if (perms == null) { perms = new ArrayList<>(); admin.put("perms", perms); }
                if (!perms.contains("*")) perms.add("*");
            }
            for (Map<String, Object> def : defaultRoles()) {
                if (!byId.containsKey(def.get("id"))) roles.add(def);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roles", roles);
        out.put("permTree", PERM_TREE);
        out.put("permCatalog", permCatalog());
        return ApiResp.ok(out);
    }

    @PutMapping("/admin/roles")
    public ApiResp<Void> saveRoles(@RequestBody Map<String, Object> body) {
        adminOnly();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newRoles = (List<Map<String, Object>>) body.get("roles");
        if (newRoles == null) throw new BizException(ErrorCode.PARAM_ERROR, "roles 必填");

        // 1) 基础字段校验
        for (Map<String, Object> r : newRoles) {
            String id = (String) r.get("id");
            String name = (String) r.get("name");
            if (id == null || id.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "角色 id 不能为空");
            if (name == null || name.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "角色「" + id + "」名称不能为空");
            if (!(r.get("perms") instanceof List)) throw new BizException(ErrorCode.PARAM_ERROR, "角色「" + id + "」perms 必须为数组");
        }

        // 2) 保护内置角色：admin 必须存在且保留 *；已标记 builtin 的角色不可删除
        boolean adminOk = false;
        for (Map<String, Object> r : newRoles) {
            if ("admin".equals(r.get("id"))) {
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) r.get("perms");
                if (perms == null || !perms.contains("*")) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "内置角色 admin 必须保留全部权限(*)");
                }
                adminOk = true;
            }
        }
        if (!adminOk) throw new BizException(ErrorCode.PARAM_ERROR, "内置角色 admin 不可删除");

        String oldRaw = readConfig("admin.roles", "[]");
        List<Map<String, Object>> oldRoles = parseList(oldRaw);
        for (Map<String, Object> r : oldRoles) {
            if (Boolean.TRUE.equals(r.get("builtin"))) {
                String bid = (String) r.get("id");
                boolean kept = newRoles.stream().anyMatch(nr -> bid.equals(nr.get("id")));
                if (!kept) throw new BizException(ErrorCode.PARAM_ERROR, "内置角色「" + bid + "」不可删除");
            }
        }

        // 3) 落库 + 写审计日志
        try {
            writeConfig("admin.roles", JSON.writeValueAsString(newRoles));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色 JSON 序列化失败");
        }
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'role', 'UPDATE_ROLES', '', '')",
                TokenContext.get().userId, TokenContext.get().role);
        return ApiResp.ok();
    }

    // ============ 内部 ============

    private String readConfig(String key, String def) {
        try {
            List<String> rs = jdbc.queryForList(
                "SELECT config_value FROM t_system_config WHERE config_key = ?", String.class, key);
            return rs.isEmpty() ? def : rs.get(0);
        } catch (Exception e) {
            return def;
        }
    }

    private void writeConfig(String key, String value) {
        jdbc.update(
            "INSERT INTO t_system_config (config_key, config_value) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)", key, value);
    }

    private List<String> parseStrList(String s) {
        try { return JSON.readValue(s, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private List<Map<String, Object>> parseList(String s) {
        try { return JSON.readValue(s, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}
