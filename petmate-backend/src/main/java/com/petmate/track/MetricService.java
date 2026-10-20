package com.petmate.track;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 漏斗指标聚合服务
 *
 * petmate.metrics.real=false → 旧估算公式（演示态兜底）
 * petmate.metrics.real=true  → 真读 t_event 表
 *
 * 商家维度 funnel 需要 merchant_id 过滤；管理员维度 funnel 跨商家。
 * 当前实现：t_event 没有 merchant_id 字段（已建表，依赖前端从商品详情传过来），
 * 商家过滤走 merchant_id 字段（NULL 表示无商家归属）。
 */
@Service
public class MetricService {

    private final JdbcTemplate jdbc;

    @Value("${petmate.metrics.real:false}")
    private boolean real;

    public MetricService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static class FunnelStats {
        public long pv;
        public long addCart;
        public long order;
        public long paid;
        public double pv2Cart;
        public double cart2Order;
        public double order2Paid;
        public boolean noTracking;

        public Map<String, Object> toMap() {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("pv", pv);
            r.put("addCart", addCart);
            r.put("order", order);
            r.put("paid", paid);
            Map<String, Object> conv = new LinkedHashMap<>();
            // 埋点可能缺失（例如用户从收藏直达下单，没有 pv/addCart 事件），分母偏小会算出 >100% 的转化率。
            // 漏斗展示层不接受超过 100% 的值，这里统一截断。
            conv.put("pv2Cart", pv == 0 ? 0.0 : rate(addCart, pv));
            conv.put("cart2Order", addCart == 0 ? 0.0 : rate(order, addCart));
            conv.put("order2Paid", order == 0 ? 0.0 : rate(paid, order));
            r.put("conversion", conv);
            r.put("noTracking", noTracking);
            return r;
        }
    }

    public FunnelStats funnel(Long merchantId, int days) {
        FunnelStats s = new FunnelStats();
        if (real) {
            String filter = "created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)";
            Object[] args = merchantId == null ? new Object[]{days} : new Object[]{days, merchantId};

            // pv（去重人数，与前端「UV」标签及加购口径一致，便于漏斗可比）
            String pvSql = "SELECT COUNT(DISTINCT user_id) FROM t_event WHERE event = 'pv' AND " + filter;
            if (merchantId != null) pvSql += " AND merchant_id = ?";
            s.pv = count(pvSql, args);

            // addCart
            String acSql = "SELECT COUNT(DISTINCT user_id) FROM t_event WHERE event = 'addCart' AND " + filter;
            if (merchantId != null) acSql += " AND merchant_id = ?";
            s.addCart = count(acSql, args);

            // order / paid 走 t_order（不依赖埋点，更准确）
            //
            // 两处口径修正：
            // 1) 商家维度必须按店铺过滤，否则商家后台看到的是全平台订单（跨商家数据泄漏，
            //    且下单数远大于本店 UV，转化率能算到 400%）；
            // 2) 与 pv/addCart 统一为「去重人数」口径，漏斗四层同量纲，转化率不会超过 100%。
            String orderScope = merchantId == null
                ? ""
                : " AND store_id IN (SELECT id FROM t_store WHERE merchant_id = ? AND is_deleted = 0)";
            Object[] orderArgs = merchantId == null ? new Object[]{days} : new Object[]{days, merchantId};

            s.order = count(
                "SELECT COUNT(DISTINCT user_id) FROM t_order WHERE is_deleted = 0 AND " + filter + orderScope,
                orderArgs);
            s.paid = count(
                "SELECT COUNT(DISTINCT user_id) FROM t_order WHERE is_deleted = 0 " +
                "AND status IN ('paid','shipping','done') AND " + filter + orderScope,
                orderArgs);
        } else {
            // 旧估算公式（与原 AdminController.funnel 完全一致）
            s.order = count(
                "SELECT COUNT(*) FROM t_order WHERE is_deleted = 0 AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)",
                new Object[]{days});
            s.paid = count(
                "SELECT COUNT(*) FROM t_order WHERE is_deleted = 0 AND status IN ('paid','shipping','done') AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)",
                new Object[]{days});
            s.addCart = count("SELECT COUNT(*) FROM t_cart WHERE is_deleted = 0", new Object[]{});
            if (s.addCart < s.order * 3) s.addCart = s.order * 3;
            s.pv = s.order * 50;
        }
        s.noTracking = real && s.order > 0 && s.pv == 0 && s.addCart == 0;
        return s;
    }

    private long count(String sql, Object[] args) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class, args);
            return n == null ? 0L : n;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 10000.0) / 100.0;
    }

    /** 转化率（百分比，0~100，超出部分截断） */
    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return Math.min(100.0, round2((double) numerator / denominator));
    }
}