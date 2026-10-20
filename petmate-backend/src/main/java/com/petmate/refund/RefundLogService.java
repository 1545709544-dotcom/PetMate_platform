package com.petmate.refund;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 售后操作流水：每一次状态流转都写一条，作为用户端「售后进度时间线」的唯一数据源。
 *
 * <p>用户端/商家端/管理端都走这里，避免各写各的导致时间线缺节点。
 */
@Service
public class RefundLogService {

    private final JdbcTemplate jdbc;

    public RefundLogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---------- action 常量 ----------
    public static final String ACT_APPLY     = "apply";     // 用户提交申请
    public static final String ACT_APPROVE   = "approve";   // 商家/平台同意
    public static final String ACT_REJECT    = "reject";    // 商家驳回
    public static final String ACT_SHIP      = "ship";      // 用户回填寄回物流
    public static final String ACT_CONFIRM   = "confirm";   // 商家确认收货
    public static final String ACT_RESHIP    = "reship";    // 商家换货重新发货
    public static final String ACT_RECEIVE   = "receive";   // 用户确认收到换货商品
    public static final String ACT_APPEAL    = "appeal";    // 用户申请平台介入
    public static final String ACT_ARBITRATE = "arbitrate"; // 平台仲裁
    public static final String ACT_CLOSE     = "close";     // 用户撤销

    public void log(long refundId, String orderNo, String actorType, Long actorId, String actorName,
                    String action, String fromStatus, String toStatus, String note) {
        jdbc.update(
            "INSERT INTO t_refund_log (refund_id, order_no, actor_type, actor_id, actor_name, " +
            "action, from_status, to_status, note) VALUES (?,?,?,?,?,?,?,?,?)",
            refundId, orderNo, actorType, actorId, actorName, action, fromStatus, toStatus, note);
    }

    public void logUser(long refundId, String orderNo, Long userId, String action,
                        String from, String to, String note) {
        log(refundId, orderNo, "user", userId, null, action, from, to, note);
    }

    public void logMerchant(long refundId, String orderNo, Long merchantId, String operatorName,
                            String action, String from, String to, String note) {
        log(refundId, orderNo, "merchant", merchantId, operatorName, action, from, to, note);
    }

    public void logAdmin(long refundId, String orderNo, Long adminId, String action,
                         String from, String to, String note) {
        log(refundId, orderNo, "admin", adminId, "平台客服", action, from, to, note);
    }

    /** 读取某售后单的完整时间线（正序，前端从上往下渲染） */
    public List<Map<String, Object>> timeline(long refundId) {
        return jdbc.queryForList(
            "SELECT id, actor_type, actor_name, action, from_status, to_status, note, created_at " +
            "FROM t_refund_log WHERE refund_id = ? ORDER BY id ASC", refundId);
    }
}
