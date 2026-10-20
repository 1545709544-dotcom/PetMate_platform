package com.petmate.product;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ProductController {
    private final JdbcTemplate jdbc;

    public ProductController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 三级分类树 */
    @GetMapping("/category/tree")
    public ApiResp<List<Map<String, Object>>> tree() {
        List<Map<String, Object>> all = jdbc.queryForList(
            "SELECT id, parent_id, name, icon, level, sort FROM t_product_category ORDER BY sort, id");
        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> r : all) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", r.get("id"));
            n.put("parentId", r.get("parent_id"));
            n.put("name", r.get("name"));
            n.put("icon", r.get("icon"));
            n.put("level", r.get("level"));
            n.put("sort", r.get("sort"));
            n.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(((Number) r.get("id")).longValue(), n);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> n : nodeMap.values()) {
            long pid = ((Number) n.get("parentId")).longValue();
            if (pid == 0) {
                roots.add(n);
            } else {
                Map<String, Object> parent = nodeMap.get(pid);
                if (parent != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(n);
                } else {
                    // 父分类缺失（数据异常/层级错乱）时不静默丢弃整棵子树，改为挂到根
                    roots.add(n);
                }
            }
        }
        return ApiResp.ok(roots);
    }

    /** 商品详情 */
    @GetMapping("/product/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.category_id, p.brand_id, p.main_image, p.sub_images, p.detail, p.detail_images, p.status, " +
            "p.sale_count, p.view_count, p.rating, p.sale_price, p.original_price, p.tags, p.specs, " +
            "(SELECT COUNT(*) FROM t_product_review WHERE product_id = p.id) AS review_count, " +
            "c.name AS category_name " +
            "FROM t_product p LEFT JOIN t_product_category c ON p.category_id = c.id " +
            "WHERE p.id = ? AND p.is_deleted = 0 AND p.status = 1", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PRODUCT_OFFLINE, "商品已下架");
        Map<String, Object> p = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.get("id"));
        out.put("name", p.get("name"));
        out.put("categoryId", p.get("category_id"));
        out.put("categoryName", p.get("category_name"));
        out.put("brandId", p.get("brand_id"));
        out.put("mainImage", p.get("main_image"));
        out.put("subImages", p.get("sub_images"));
        out.put("detailImages", p.get("detail_images"));
        out.put("detail", p.get("detail"));
        out.put("salePrice", p.get("sale_price"));
        out.put("originalPrice", p.get("original_price"));
        out.put("saleCount", p.get("sale_count"));
        out.put("rating", p.get("rating"));
        out.put("reviewCount", p.get("review_count"));
        out.put("tags", p.get("tags"));
        out.put("specs", p.get("specs"));
        // 补回 status：前端编辑页依赖此字段回传，缺失会导致下架商品被静默重新上架
        out.put("status", p.get("status"));
        // SKU（仅取未软删的）
        List<Map<String, Object>> skus = jdbc.queryForList(
            "SELECT id, product_id, sku_code, spec_values, price, stock, image FROM t_product_sku WHERE product_id = ? AND is_deleted = 0", id);
        out.put("skus", skus);
        // 浏览 +1（已在校验 status=1 之后，下架商品不会走到这里）
        jdbc.update("UPDATE t_product SET view_count = view_count + 1 WHERE id = ?", id);
        return ApiResp.ok(out);
    }

    /** 记录商品浏览足迹（登录态）：1h 内重复浏览只刷新时间不累加次数 */
    @PostMapping("/product/{id}/footprint")
    public ApiResp<Void> recordFootprint(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        // 商品需存在且在售（下架商品详情页本就不会触发记录）
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND is_deleted = 0 AND status = 1", Integer.class, id);
        if (exists == null || exists == 0) {
            throw new BizException(ErrorCode.PRODUCT_OFFLINE, "商品不存在或已下架");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id, last_view_at, is_deleted FROM t_product_footprint WHERE user_id = ? AND product_id = ? AND type = 'product' LIMIT 1",
            uid, id);
        if (exist.isEmpty()) {
            jdbc.update(
                "INSERT INTO t_product_footprint (user_id, product_id, type, view_count, last_view_at, created_at, is_deleted) " +
                "VALUES (?, ?, 'product', 1, ?, ?, 0)",
                uid, id, java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
        } else {
            Map<String, Object> row = exist.get(0);
            long fid = ((Number) row.get("id")).longValue();
            Object lvObj = row.get("last_view_at");
            java.time.LocalDateTime last = null;
            if (lvObj instanceof java.sql.Timestamp ts) last = ts.toLocalDateTime();
            else if (lvObj instanceof java.time.LocalDateTime ldt) last = ldt;
            if (last != null && last.plusHours(1).isAfter(now)) {
                jdbc.update("UPDATE t_product_footprint SET last_view_at = ?, is_deleted = 0 WHERE id = ?",
                    java.sql.Timestamp.valueOf(now), fid);
            } else {
                jdbc.update("UPDATE t_product_footprint SET view_count = view_count + 1, last_view_at = ?, is_deleted = 0 WHERE id = ?",
                    java.sql.Timestamp.valueOf(now), fid);
            }
        }
        return ApiResp.ok(null);
    }

    /** 按分类列出 */
    @GetMapping("/product/list")
    public ApiResp<PageResp<Map<String, Object>>> listByCategory(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String where = "WHERE is_deleted = 0 AND status = 1";
        Object[] args;
        if (categoryId != null) {
            where += " AND category_id = ?";
            args = new Object[]{categoryId};
        } else {
            args = new Object[]{};
        }
        return ApiResp.ok(paginate("SELECT id, name, category_id, main_image, sale_price, original_price, sale_count, rating, tags, created_at, " +
                "(SELECT MIN(s.id) FROM t_product_sku s WHERE s.product_id = t_product.id AND s.is_deleted = 0) AS default_sku_id, " +
                "(SELECT COUNT(*) FROM t_product_review r WHERE r.product_id = t_product.id) AS review_count FROM t_product " + where,
                where, args, page, size));
    }

    /** 全局搜索（多字段 + 分词匹配，避免「猫粮」这类组合词搜不到） */
    @GetMapping("/search")
    public ApiResp<PageResp<Map<String, Object>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return ApiResp.ok(PageResp.of(new ArrayList<>(), 0, page, size));
        }
        List<String> terms = splitKeyword(kw);
        StringBuilder where = new StringBuilder("WHERE is_deleted = 0 AND status = 1");
        List<Object> argList = new ArrayList<>();
        // 每个分词都必须命中（AND），单个分词可命中 名称 / 标签 / 副标题 / 所属分类名（OR）
        for (String t : terms) {
            String like = "%" + escapeLike(t) + "%";
            where.append(" AND (name LIKE ? ESCAPE '!' OR IFNULL(tags,'') LIKE ? ESCAPE '!'")
                 .append(" OR IFNULL(detail,'') LIKE ? ESCAPE '!'")
                 .append(" OR category_id IN (SELECT id FROM t_product_category WHERE name LIKE ? ESCAPE '!'))");
            argList.add(like); argList.add(like); argList.add(like); argList.add(like);
        }
        if (categoryId != null) {
            where.append(" AND category_id = ?");
            argList.add(categoryId);
        }
        // 相关度排序：整串命中名称 > 任一分词命中名称 > 销量
        String fullLike = "%" + escapeLike(kw) + "%";
        String orderBy = " ORDER BY (CASE WHEN name LIKE ? ESCAPE '!' THEN 0 ELSE 1 END), sale_count DESC, id DESC";
        String w = where.toString();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_product " + w, Long.class, argList.toArray());
        List<Object> rowArgs = new ArrayList<>(argList);
        rowArgs.add(fullLike); rowArgs.add(safeSize); rowArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, category_id, main_image, sale_price, original_price, sale_count, rating, tags, created_at, " +
                "(SELECT MIN(s.id) FROM t_product_sku s WHERE s.product_id = t_product.id AND s.is_deleted = 0) AS default_sku_id, " +
                "(SELECT COUNT(*) FROM t_product_review r WHERE r.product_id = t_product.id) AS review_count FROM t_product " +
                w + orderBy + " LIMIT ? OFFSET ?", rowArgs.toArray());
        return ApiResp.ok(PageResp.of(rows.stream().map(this::normalize).toList(),
                total == null ? 0 : total, safePage, safeSize));
    }

    /** 把搜索词切成检索单元：先按空白切，再对无空白的中文长词做 2-gram 兜底 */
    private static List<String> splitKeyword(String kw) {
        List<String> terms = new ArrayList<>();
        for (String part : kw.split("\\s+")) {
            if (!part.isBlank()) terms.add(part);
        }
        // 单个纯中文词且长度 2-6：同时按单字拆，实现「猫粮」→ 猫 AND 粮
        if (terms.size() == 1) {
            String only = terms.get(0);
            if (only.length() >= 2 && only.length() <= 6 && only.chars().allMatch(c -> c >= 0x4E00 && c <= 0x9FFF)) {
                List<String> chars = new ArrayList<>();
                for (char c : only.toCharArray()) chars.add(String.valueOf(c));
                return chars;
            }
        }
        return terms.isEmpty() ? List.of(kw) : terms;
    }

    /** LIKE 通配符转义，'!' 作为转义字符 */
    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** 相关推荐 (同类目其他商品) */
    @GetMapping("/recommend/related")
    public ApiResp<List<Map<String, Object>>> related(@RequestParam long productId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, category_id, main_image, sale_price, original_price, sale_count, rating, " +
            "(SELECT COUNT(*) FROM t_product_review r WHERE r.product_id = t_product.id) AS review_count FROM t_product " +
            "WHERE is_deleted = 0 AND status = 1 AND id <> ? AND category_id = (SELECT category_id FROM t_product WHERE id = ?) " +
            "ORDER BY sale_count DESC LIMIT 6", productId, productId);
        return ApiResp.ok(rows.stream().map(this::normalize).toList());
    }

    /** 首页 feed (新品+热门) */
    @GetMapping("/recommend/feed")
    public ApiResp<List<Map<String, Object>>> feed() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.name, p.category_id, p.main_image, p.sale_price, p.original_price, p.sale_count, p.rating, " +
            "(SELECT MIN(s.id) FROM t_product_sku s WHERE s.product_id = p.id AND s.is_deleted = 0) AS default_sku_id, " +
            "(SELECT COUNT(*) FROM t_product_review r WHERE r.product_id = p.id) AS review_count " +
            "FROM t_product p WHERE p.is_deleted = 0 AND p.status = 1 " +
            "ORDER BY p.sale_count DESC, p.view_count DESC LIMIT 12");
        return ApiResp.ok(rows.stream().map(r -> {
            Map<String, Object> m = normalize(r);
            m.put("defaultSkuId", r.get("default_sku_id"));
            return m;
        }).toList());
    }

    private PageResp<Map<String, Object>> paginate(String baseSql, String where, Object[] args, int page, int size) {
        int offset = (page - 1) * size;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_product " + where, Long.class, args);
        List<Map<String, Object>> rows = jdbc.queryForList(baseSql + " ORDER BY id DESC LIMIT ? OFFSET ?",
                appendArgs(args, size, offset));
        return PageResp.of(rows.stream().map(this::normalize).toList(), total == null ? 0 : total, page, size);
    }

    private Object[] appendArgs(Object[] base, Object... more) {
        Object[] all = new Object[base.length + more.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return all;
    }

    private Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("name", r.get("name"));
        m.put("categoryId", r.get("category_id"));
        m.put("mainImage", r.get("main_image"));
        m.put("salePrice", r.get("sale_price"));
        m.put("originalPrice", r.get("original_price"));
        m.put("saleCount", r.get("sale_count"));
        m.put("rating", r.get("rating"));
        m.put("reviewCount", r.get("review_count"));
        m.put("tags", r.get("tags"));
        m.put("createdAt", r.get("created_at"));
        m.put("defaultSkuId", r.get("default_sku_id"));
        return m;
    }
}
