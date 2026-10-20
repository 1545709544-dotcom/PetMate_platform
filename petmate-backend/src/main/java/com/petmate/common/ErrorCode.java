package com.petmate.common;

/**
 * 错误码段（PRD §7.3）
 *  0      成功
 *  1xxxx  通用
 *  2xxxx  用户域
 *  3xxxx  商品/服务
 *  4xxxx  订单
 *  5xxxx  支付
 *  6xxxx  内容/社区
 *  7xxxx  第三方依赖
 *  9xxxx  后台管理
 */
public final class ErrorCode {
    private ErrorCode() {}

    public static final int SUCCESS                  = 0;
    public static final int PARAM_ERROR              = 10001;
    public static final int SYSTEM_ERROR             = 10500;
    public static final int NOT_LOGIN                = 20001;
    public static final int PASSWORD_ERROR           = 20002;
    public static final int ACCOUNT_BANNED           = 20003;
    public static final int STOCK_NOT_ENOUGH         = 30001;
    public static final int PRODUCT_OFFLINE          = 30002;
    public static final int ORDER_NOT_FOUND          = 40001;
    public static final int ORDER_STATUS_INVALID     = 40002;
    public static final int BALANCE_NOT_ENOUGH       = 50001;
    public static final int PAY_TIMEOUT              = 50002;
    public static final int PAY_CREATE_FAILED        = 50003;
    public static final int PAY_SIGN_INVALID         = 50004;
    public static final int PAY_ORDER_MISMATCH       = 50005;
    public static final int PAY_AMOUNT_MISMATCH      = 50006;
    public static final int REFUND_FAILED            = 50007;
    public static final int COUPON_ALREADY_CLAIMED   = 50011;
    public static final int COUPON_OUT_OF_STOCK      = 50012;
    public static final int POINTS_NOT_ENOUGH        = 50013;
    public static final int POINTS_REDEEM_EXCEED     = 50014;
    public static final int SENSITIVE_WORD           = 60001;
    public static final int NEED_REALNAME            = 60002;
    public static final int AI_BUSY                  = 70001;
    public static final int AI_UPSTREAM_ERROR        = 70002;
    public static final int AI_RESPONSE_INVALID      = 70003;
    public static final int CONSULT_QUOTA_EXCEEDED   = 70004;
    public static final int NO_PERMISSION            = 90001;

    public static String defaultMessage(int code) {
        return switch (code) {
            case SUCCESS -> "ok";
            case PARAM_ERROR -> "参数错误";
            case SYSTEM_ERROR -> "系统异常";
            case NOT_LOGIN -> "未登录，请重新登录";
            case PASSWORD_ERROR -> "账号或密码错误";
            case ACCOUNT_BANNED -> "账号已被封禁";
            case STOCK_NOT_ENOUGH -> "库存不足";
            case PRODUCT_OFFLINE -> "商品已下架";
            case ORDER_NOT_FOUND -> "订单不存在";
            case ORDER_STATUS_INVALID -> "订单状态异常";
            case BALANCE_NOT_ENOUGH -> "余额不足";
            case PAY_TIMEOUT -> "支付超时";
            case PAY_CREATE_FAILED -> "创建预支付订单失败";
            case PAY_SIGN_INVALID -> "异步通知签名校验失败";
            case PAY_ORDER_MISMATCH -> "通知与订单不匹配";
            case PAY_AMOUNT_MISMATCH -> "通知金额与订单不一致";
            case REFUND_FAILED -> "退款失败";
            case COUPON_ALREADY_CLAIMED -> "已领取该优惠券";
            case COUPON_OUT_OF_STOCK -> "优惠券已被领完";
            case POINTS_NOT_ENOUGH -> "鱼干余额不足";
            case POINTS_REDEEM_EXCEED -> "鱼干抵扣超出订单可用上限";
            case SENSITIVE_WORD -> "内容包含敏感词";
            case NEED_REALNAME -> "请先完成实名认证后再发布动态";
            case AI_BUSY -> "AI 服务繁忙，请稍后再试";
            case AI_UPSTREAM_ERROR -> "上游 AI 调用异常";
            case AI_RESPONSE_INVALID -> "AI 返回结构非法";
            case CONSULT_QUOTA_EXCEEDED -> "AI 问诊次数已用尽";
            case NO_PERMISSION -> "无权限操作";
            default -> "请求失败";
        };
    }
}