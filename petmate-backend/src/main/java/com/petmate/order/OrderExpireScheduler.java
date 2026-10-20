package com.petmate.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.petmate.points.PointsService;

import java.util.List;
import java.util.Map;

/**
 * 待支付订单 30 分钟超时自动取消。
 *
 * 依赖：
 *  - t_order.expire_at (v3 migrate 加列)
 *  - PetMateApplication 已 @EnableScheduling
 *
 * 策略：
 *  - 每分钟扫描一次 status='pending_pay' AND expire_at < NOW() 的订单。
 *  - 「先抢占、再回退」：用带 status='pending_pay' 条件的 CAS 更新抢下这单，抢到才回退资产。
 *    历史实现是「先回退资产、最后无条件置 cancelled」，如果用户恰好在扫描与更新之间完成支付，
 *    会出现「已付款订单被判超时取消 + 库存凭空回补」的库存虚增和资金事故。
 *  - 每单独立事务：单笔失败只回滚该笔，不会让整轮扫描半途而废并留下写了一半的脏数据。
 *  - 回退：库存 + 销量、秒杀 sold、优惠券（置回 available）、鱼干（PointsService.refund）。
 */
@Component
public class OrderExpireScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireScheduler.class);

    private final JdbcTemplate jdbc;
    private final PointsService pointsService;
    private final TransactionTemplate tx;

    public OrderExpireScheduler(JdbcTemplate jdbc, PointsService pointsService,
                                PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.pointsService = pointsService;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void sweep() {
        List<Map<String, Object>> expired;
        try {
            expired = jdbc.queryForList(
                "SELECT order_no, user_id, id, coupon_id, points_used, remark " +
                "FROM t_order WHERE status = 'pending_pay' AND expire_at IS NOT NULL AND expire_at < NOW() " +
                "AND is_deleted = 0");
        } catch (Exception e) {
            log.warn("[order-expire] scan error", e);
            return;
        }

        int cancelled = 0;
        for (Map<String, Object> o : expired) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> cancelOne(o)))) cancelled++;
            } catch (Exception e) {
                log.warn("[order-expire] cancel failed, orderNo={}", o.get("order_no"), e);
            }
        }
        if (cancelled > 0) {
            log.info("[order-expire] cancelled {}/{} pending_pay orders", cancelled, expired.size());
        }
    }

    /** 处理单笔超时订单，返回是否真的取消了（未抢到说明已被支付或已取消） */
    private boolean cancelOne(Map<String, Object> o) {
        String orderNo = (String) o.get("order_no");
        long uid = ((Number) o.get("user_id")).longValue();
        long orderId = ((Number) o.get("id")).longValue();

        // 1) CAS 抢占：只有仍处于 pending_pay 才允许判超时，杜绝「支付成功后被取消」
        int claimed = jdbc.update(
            "UPDATE t_order SET status = 'cancelled' WHERE id = ? AND status = 'pending_pay'", orderId);
        if (claimed == 0) {
            log.debug("[order-expire] skip {}, already paid or cancelled", orderNo);
            return false;
        }

        // 2) 回补库存 + 扣减销量
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT product_id, sku_id, quantity FROM t_order_item WHERE order_id = ?", orderId);
        for (Map<String, Object> it : items) {
            int q = ((Number) it.get("quantity")).intValue();
            Object skuId = it.get("sku_id");
            if (skuId != null) {
                jdbc.update("UPDATE t_product_sku SET stock = stock + ? WHERE id = ?",
                    q, ((Number) skuId).longValue());
            }
            jdbc.update("UPDATE t_product SET sale_count = GREATEST(sale_count - ?, 0) WHERE id = ?",
                q, ((Number) it.get("product_id")).longValue());
        }

        // 3) 秒杀单：回补 sold（避免秒杀名额被占死）
        if ("秒杀订单".equals(o.get("remark"))) {
            jdbc.update(
                "UPDATE t_seckill s JOIN t_order_item oi ON oi.product_id = s.product_id " +
                "SET s.sold = GREATEST(s.sold - 1, 0) WHERE oi.order_id = ?", orderId);
        }

        // 4) 回退优惠券（合法状态只有 available / used / expired，写 'unused' 会让券从两个列表里同时消失）
        Object cid = o.get("coupon_id");
        if (cid != null) {
            jdbc.update(
                "UPDATE t_user_coupon SET status = 'available', used_at = NULL " +
                "WHERE user_id = ? AND coupon_id = ? AND status = 'used'",
                uid, ((Number) cid).longValue());
        }

        // 5) 回退鱼干
        Object pu = o.get("points_used");
        int pointsUsed = pu == null ? 0 : ((Number) pu).intValue();
        if (pointsUsed > 0) pointsService.refund(uid, pointsUsed, "订单超时取消退回", orderId);

        return true;
    }
}
