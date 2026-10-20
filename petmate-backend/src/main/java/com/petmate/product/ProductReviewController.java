package com.petmate.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品评价（C 端 + 后台）。
 *  - 列表 / 详情 / 提交 走 t_product_review
 *  - summary 用 AVG/COUNT/分组聚合
 */
@RestController
public class ProductReviewController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final com.petmate.common.NotifyService notify;

    public ProductReviewController(JdbcTemplate jdbc, com.petmate.common.NotifyService notify) {
        this.jdbc = jdbc;
        this.notify = notify;
    }

    /** 商品评价分页 */
    @GetMapping("/product/{productId}/reviews")
    public ApiResp<PageResp<Map<String, Object>>> list(@PathVariable long productId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review WHERE product_id = ? AND is_deleted = 0",
            Long.class, productId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, product_id, user_id, user_nickname, user_avatar, rating, content, images, reply, replied_at, created_at " +
            "FROM t_product_review WHERE product_id = ? AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT ? OFFSET ?", productId, size, offset);
        return ApiResp.ok(PageResp.of(rows.stream().map(this::normalize).toList(),
                total == null ? 0 : total, page, size));
    }

    /** 评价汇总 */
    @GetMapping("/product/{productId}/reviews/summary")
    public ApiResp<Map<String, Object>> summary(@PathVariable long productId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Double avg = jdbc.queryForObject(
            "SELECT COALESCE(AVG(rating), 0) FROM t_product_review WHERE product_id = ? AND is_deleted = 0",
            Double.class, productId);
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review WHERE product_id = ? AND is_deleted = 0",
            Long.class, productId);
        out.put("averageRating", avg == null ? 0.0 : Math.round(avg * 10) / 10.0);
        out.put("totalReviews", total == null ? 0 : total);

        // 各星级数量
        List<Map<String, Object>> buckets = jdbc.queryForList(
            "SELECT rating, COUNT(*) AS cnt FROM t_product_review " +
            "WHERE product_id = ? AND is_deleted = 0 GROUP BY rating", productId);
        Map<String, Long> ratingCounts = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) ratingCounts.put(String.valueOf(i), 0L);
        for (Map<String, Object> b : buckets) {
            int r = ((Number) b.get("rating")).intValue();
            long c = ((Number) b.get("cnt")).longValue();
            ratingCounts.put(String.valueOf(r), c);
        }
        out.put("ratingCounts", ratingCounts);
        return ApiResp.ok(out);
    }

    /** 当前用户是否具备评价资格（购买过该商品） */
    @GetMapping("/product/{productId}/review/eligible")
    public ApiResp<Map<String, Object>> eligible(@PathVariable long productId) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eligible", hasPurchased(uid, productId));
        return ApiResp.ok(out);
    }

    /** 提交评价 */
    @Transactional
    @PostMapping("/product/{productId}/review")
    public ApiResp<Map<String, Object>> create(@PathVariable long productId,
                                               @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        // 商品存在校验
        Integer pCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product WHERE id = ? AND is_deleted = 0 AND status = 1",
            Integer.class, productId);
        if (pCnt == null || pCnt == 0) throw new BizException(ErrorCode.PRODUCT_OFFLINE, "商品不存在或已下架");

        // orderNo 必填：评价与订单绑定，每单每商品只能评价一次
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj == null || String.valueOf(orderNoObj).isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少订单号，无法提交评价");
        String orderNo = String.valueOf(orderNoObj).trim();

        // 校验订单归属 + 状态（必须已完成收货、待评价）
        List<Map<String, Object>> orders = jdbc.queryForList(
            "SELECT id, status FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0",
            orderNo, uid);
        if (orders.isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "订单不存在或不属于当前用户");
        long orderId = ((Number) orders.get(0).get("id")).longValue();
        String orderStatus = String.valueOf(orders.get(0).get("status"));
        if (!orderStatus.equals("done"))
            throw new BizException(ErrorCode.PARAM_ERROR, "订单当前状态不支持评价");

        // 校验该订单确实包含此商品
        Integer itemCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order_item WHERE order_id = ? AND product_id = ?",
            Integer.class, orderId, productId);
        if (itemCnt == null || itemCnt == 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "该订单未包含此商品");

        // 去重：每单每商品只能评价一次
        Integer reviewedCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review WHERE product_id = ? AND user_id = ? AND order_id = ? AND is_deleted = 0",
            Integer.class, productId, uid, orderId);
        if (reviewedCnt != null && reviewedCnt > 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "您已评价过该商品，每笔订单的商品只能评价一次");

        Object ratingObj = body.get("rating");
        if (ratingObj == null) throw new BizException(ErrorCode.PARAM_ERROR, "rating 必填");
        int rating = ((Number) ratingObj).intValue();
        if (rating < 1 || rating > 5) throw new BizException(ErrorCode.PARAM_ERROR, "rating 必须在 1-5 之间");

        // 自动取用户昵称/头像
        Map<String, Object> u = jdbc.queryForMap(
            "SELECT nickname, avatar FROM t_user WHERE id = ? AND is_deleted = 0", uid);

        String imagesJson = null;
        if (body.get("images") != null) {
            try { imagesJson = json.writeValueAsString(body.get("images")); } catch (Exception ignore) {}
        }

        jdbc.update(
            "INSERT INTO t_product_review (product_id, order_id, user_id, user_nickname, user_avatar, rating, content, images) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            productId, orderId, uid, u.get("nickname"), u.get("avatar"),
            rating, body.get("content"), imagesJson);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 同事务回写 SPU 真实评分（修复 rating 恒为 5.00 假数据）：取该商品全部有效评价均值（1 位小数），无评价则回退 5.0
        jdbc.update(
            "UPDATE t_product SET rating = (SELECT COALESCE(ROUND(AVG(rating), 1), 5.0) FROM t_product_review WHERE product_id = ? AND is_deleted = 0) WHERE id = ?",
            productId, productId);

        // 通知商家：收到新评价
        Long mId = jdbc.queryForObject("SELECT merchant_id FROM t_product WHERE id = ?", Long.class, productId);
        if (mId != null) {
            notify.notify(mId, "review", "收到新评价",
                "商品 #" + productId + " 收到 " + rating + " 星评价", String.valueOf(id));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("productId", productId);
        out.put("orderId", orderId);
        out.put("userId", uid);
        out.put("rating", rating);
        return ApiResp.ok(out);
    }

    /** 查询某订单已评价的商品 ID 列表 + 评价详情（前端打开评价弹窗时展示已评价内容） */
    @GetMapping("/product/review/check")
    public ApiResp<Map<String, Object>> checkReviewed(@RequestParam String orderNo) {
        long uid = TokenContext.requireUserId();
        List<Long> reviewedProductIds = new java.util.ArrayList<>();
        List<Map<String, Object>> reviews = new java.util.ArrayList<>();
        List<Map<String, Object>> orders = jdbc.queryForList(
            "SELECT id FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        if (!orders.isEmpty()) {
            long orderId = ((Number) orders.get(0).get("id")).longValue();
            reviews = jdbc.queryForList(
                "SELECT product_id, rating, content, images, created_at " +
                "FROM t_product_review WHERE order_id = ? AND user_id = ? AND is_deleted = 0",
                orderId, uid);
            for (Map<String, Object> r : reviews) {
                reviewedProductIds.add(((Number) r.get("product_id")).longValue());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reviewedProductIds", reviewedProductIds);
        out.put("reviews", reviews.stream().map(this::normalizeMyReview).toList());
        return ApiResp.ok(out);
    }

    /** 是否购买过该商品（存在含本商品且已支付/发货/完成的未删除订单） */
    private boolean hasPurchased(long uid, long productId) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order o JOIN t_order_item i ON i.order_id = o.id " +
            "WHERE o.user_id = ? AND i.product_id = ? " +
            "AND o.status IN ('paid','shipping','done') AND o.is_deleted = 0",
            Integer.class, uid, productId);
        return cnt != null && cnt > 0;
    }

    private Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("productId", r.get("product_id"));
        m.put("userId", r.get("user_id"));
        m.put("userNickname", r.get("user_nickname"));
        m.put("userAvatar", r.get("user_avatar"));
        m.put("rating", r.get("rating"));
        m.put("content", r.get("content"));
        m.put("images", r.get("images"));
        // 商家回复：前端据此展示「商家回复」区块，闭环才算完整
        m.put("reply", r.get("reply"));
        m.put("repliedAt", r.get("replied_at"));
        m.put("createdAt", r.get("created_at"));
        return m;
    }

    private Map<String, Object> normalizeMyReview(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", r.get("product_id"));
        m.put("rating", r.get("rating"));
        m.put("content", r.get("content"));
        m.put("images", r.get("images"));
        m.put("createdAt", r.get("created_at"));
        return m;
    }
}
