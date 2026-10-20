package com.petmate.seckill;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    private final JdbcTemplate jdbc;

    public SeckillController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 进行中的秒杀列表（在时间内 + 上架 + 有库存）。 */
    @GetMapping("/list")
    public ApiResp<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.id, s.product_id, s.title, s.seckill_price, s.stock, s.sold, s.start_time, s.end_time, " +
            "p.name AS product_name, p.main_image AS product_image " +
            "FROM t_seckill s JOIN t_product p ON s.product_id = p.id " +
            "WHERE s.is_deleted = 0 AND s.status = 1 AND s.start_time <= NOW() AND s.end_time >= NOW() " +
            "ORDER BY s.end_time ASC");
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(normalize(r));
        return ApiResp.ok(out);
    }

    @GetMapping("/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.id, s.product_id, s.title, s.seckill_price, s.stock, s.sold, s.start_time, s.end_time, " +
            "p.name AS product_name, p.main_image AS product_image, p.sale_price AS original_price " +
            "FROM t_seckill s JOIN t_product p ON s.product_id = p.id " +
            "WHERE s.id = ? AND s.is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "秒杀活动不存在");
        return ApiResp.ok(normalize(rows.get(0)));
    }

    /**
     * 抢购：原子扣减秒杀库存，售罄/超时则失败；成功则生成一笔 pending_pay 订单，
     * 前端拿到 orderNo 后跳转到 /pay/:orderNo 走既有支付流程。
     */
    @PostMapping("/{id}/grab")
    @Transactional
    public ApiResp<Map<String, Object>> grab(@PathVariable long id) {
        long uid = TokenContext.requireUserId();

        List<Map<String, Object>> act = jdbc.queryForList(
            "SELECT id, product_id, title, seckill_price " +
            "FROM t_seckill WHERE id = ? AND is_deleted = 0 AND status = 1", id);
        if (act.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "秒杀活动不存在或已下架");
        Map<String, Object> a = act.get(0);
        long productId = ((Number) a.get("product_id")).longValue();
        BigDecimal seckillPrice = (BigDecimal) a.get("seckill_price");

        // 每人限购 1 件：统计该用户在本商品秒杀下的有效订单（秒杀订单 remark=秒杀订单，排除已取消）
        Integer bought = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order o JOIN t_order_item oi ON oi.order_id = o.id " +
            "WHERE o.user_id = ? AND oi.product_id = ? AND o.remark = '秒杀订单' " +
            "AND o.status != 'cancelled' AND o.is_deleted = 0",
            Integer.class, uid, productId);
        if (bought != null && bought >= 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该秒杀活动每人限购 1 件");
        }

        // 原子扣减（sold+1 仅在 sold<stock 且处于时间窗口内生效）
        int updated = jdbc.update(
            "UPDATE t_seckill SET sold = sold + 1 " +
            "WHERE id = ? AND sold < stock AND start_time <= NOW() AND end_time >= NOW()", id);
        if (updated == 0) throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, "手慢了，已被抢光或活动未开始/已结束");

        // 取商品名/图 + 首个可用 sku
        List<Map<String, Object>> prod = jdbc.queryForList(
            "SELECT name, main_image FROM t_product WHERE id = ?", productId);
        List<Map<String, Object>> sku = jdbc.queryForList(
            "SELECT id, stock FROM t_product_sku WHERE product_id = ? ORDER BY id ASC LIMIT 1", productId);
        if (prod.isEmpty()) throw new BizException(ErrorCode.PRODUCT_OFFLINE);
        if (sku.isEmpty()) throw new BizException(ErrorCode.PRODUCT_OFFLINE, "该商品无可用规格");
        long skuId = ((Number) sku.get(0).get("id")).longValue();

        // 扣商品库存（CAS：仅当 stock>=1，避免超卖/负库存）+ 加销量
        int skuAffected = jdbc.update("UPDATE t_product_sku SET stock = stock - 1 WHERE id = ? AND stock >= 1", skuId);
        if (skuAffected == 0) {
            // 库存不足，回补秒杀名额并提示
            jdbc.update("UPDATE t_seckill SET sold = sold - 1 WHERE id = ? AND sold > 0", id);
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, "库存不足，抢购失败");
        }
        jdbc.update("UPDATE t_product SET sale_count = sale_count + 1 WHERE id = ?", productId);

        // 生成订单（带 expire_at，未支付则由 OrderExpireScheduler 自动取消并回补库存/名额）
        String orderNo = "P" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        jdbc.update(
            "INSERT INTO t_order (order_no, user_id, total_amount, pay_amount, freight_amount, status, remark, expire_at) " +
            "VALUES (?, ?, ?, ?, 0, 'pending_pay', '秒杀订单', DATE_ADD(NOW(), INTERVAL 15 MINUTE))",
            orderNo, uid, seckillPrice, seckillPrice);
        long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
            "INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity) " +
            "VALUES (?, ?, ?, ?, ?, ?, 1)",
            orderId, productId, skuId, prod.get(0).get("name"), prod.get(0).get("main_image"), seckillPrice);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo);
        out.put("seckillPrice", seckillPrice);
        return ApiResp.ok(out);
    }

    private Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("productId", r.get("product_id"));
        m.put("title", r.get("title"));
        m.put("seckillPrice", r.get("seckill_price"));
        m.put("stock", r.get("stock"));
        m.put("sold", r.get("sold"));
        m.put("startTime", r.get("start_time") == null ? null : r.get("start_time").toString());
        m.put("endTime", r.get("end_time") == null ? null : r.get("end_time").toString());
        m.put("productName", r.get("product_name"));
        m.put("productImage", r.get("product_image"));
        if (r.containsKey("original_price")) m.put("originalPrice", r.get("original_price"));
        return m;
    }
}
