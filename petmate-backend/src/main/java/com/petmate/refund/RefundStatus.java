package com.petmate.refund;

import java.util.List;
import java.util.Map;

/**
 * 售后状态机常量与流转规则。
 *
 * <p>三条主线：
 * <pre>
 *  仅退款   pending → refunded / rejected
 *  退货退款 pending → returning  → returned → refunded / rejected
 *  换货     pending → exchanging → returned → reshipped → exchanged / rejected
 *  介入线   rejected(或进行中) → appealing → arbitrated
 * </pre>
 *
 * <p>历史 DDL 注释里的 {@code approved} 是幽灵态，代码从未写入，v8 迁移已归一到 pending，不再使用。
 */
public final class RefundStatus {

    private RefundStatus() {}

    // ---------- 售后类型 ----------
    public static final String TYPE_REFUND_ONLY   = "REFUND_ONLY";   // 仅退款
    public static final String TYPE_RETURN_REFUND = "RETURN_REFUND"; // 退货退款
    public static final String TYPE_EXCHANGE      = "EXCHANGE";      // 换货

    // ---------- 状态 ----------
    public static final String PENDING    = "pending";    // 待商家处理
    public static final String RETURNING  = "returning";  // 已同意退货，待用户寄回
    public static final String EXCHANGING = "exchanging"; // 已同意换货，待用户寄回
    public static final String RETURNED   = "returned";   // 用户已寄回，待商家收货
    public static final String RESHIPPED  = "reshipped";  // 换货：商家已重发，待用户确认
    public static final String REFUNDED   = "refunded";   // 已退款（终态）
    public static final String EXCHANGED  = "exchanged";  // 换货完成（终态）
    public static final String REJECTED   = "rejected";   // 商家驳回（终态，可申请介入）
    public static final String APPEALING  = "appealing";  // 平台介入中
    public static final String ARBITRATED = "arbitrated"; // 平台已仲裁（终态）
    public static final String CLOSED     = "closed";     // 用户撤销（终态）

    /** 终态集合：不可再被商家处理 */
    public static final List<String> FINAL_STATUS =
        List.of(REFUNDED, EXCHANGED, REJECTED, ARBITRATED, CLOSED);

    /** 用户可回填寄回物流的状态 */
    public static final List<String> CAN_FILL_LOGISTICS = List.of(RETURNING, EXCHANGING);

    /** 用户可撤销申请的状态（商家尚未实质处理完） */
    public static final List<String> CAN_CANCEL = List.of(PENDING, RETURNING, EXCHANGING);

    /** 用户可申请平台介入的状态：被驳回，或流程停滞在商家侧 */
    public static final List<String> CAN_APPEAL = List.of(REJECTED, PENDING, RETURNED, RESHIPPED);

    /** 商家待办（需要商家动作） */
    public static final List<String> MERCHANT_TODO = List.of(PENDING, RETURNED);

    /** 允许发起售后的订单状态 */
    public static final List<String> ORDER_CAN_APPLY = List.of("paid", "shipping", "done");

    private static final Map<String, String> TYPE_TEXT = Map.of(
        TYPE_REFUND_ONLY,   "仅退款",
        TYPE_RETURN_REFUND, "退货退款",
        TYPE_EXCHANGE,      "换货"
    );

    private static final Map<String, String> STATUS_TEXT = Map.ofEntries(
        Map.entry(PENDING,    "待商家处理"),
        Map.entry(RETURNING,  "待寄回"),
        Map.entry(EXCHANGING, "待寄回"),
        Map.entry(RETURNED,   "待商家收货"),
        Map.entry(RESHIPPED,  "商家已重发"),
        Map.entry(REFUNDED,   "退款成功"),
        Map.entry(EXCHANGED,  "换货完成"),
        Map.entry(REJECTED,   "商家已驳回"),
        Map.entry(APPEALING,  "平台介入中"),
        Map.entry(ARBITRATED, "平台已裁定"),
        Map.entry(CLOSED,     "已撤销")
    );

    public static boolean isValidType(String type) {
        return TYPE_TEXT.containsKey(type);
    }

    public static boolean isFinal(String status) {
        return FINAL_STATUS.contains(status);
    }

    public static boolean needReturn(String type) {
        return TYPE_RETURN_REFUND.equals(type) || TYPE_EXCHANGE.equals(type);
    }

    public static String typeText(String type) {
        return TYPE_TEXT.getOrDefault(type, "售后");
    }

    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status == null ? "" : status);
    }

    /** 商家同意后应进入的状态：仅退款直接退款完成，退货/换货进入待寄回 */
    public static String afterApprove(String type) {
        if (TYPE_EXCHANGE.equals(type)) return EXCHANGING;
        if (TYPE_RETURN_REFUND.equals(type)) return RETURNING;
        return REFUNDED;
    }
}
