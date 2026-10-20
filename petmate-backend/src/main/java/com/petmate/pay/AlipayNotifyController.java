package com.petmate.pay;

import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.points.PointsService;
import com.petmate.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝异步通知入口
 *
 *   POST /pay/alipay/notify   (form-encoded)
 *
 * 流程：
 *  1) 收集 request.getParameterMap()
 *  2) AlipayClient.verifyNotify 验签（公钥）
 *  3) trade_status == TRADE_SUCCESS 才处理
 *  4) t_payment_log 幂等（同 out_trade_no 已 success 直接返回 success）
 *  5) 反查 t_order 校验订单存在 + 金额一致 + 状态 pending_pay
 *  6) UPDATE t_order SET status='paid', paid_at=NOW(), trade_no, pay_channel='alipay'
 *  7) INSERT t_payment_log action=notify status=success
 *  8) return "success"（支付宝要求字符串）
 *
 * 不通过 JwtAuthFilter：异步通知不带 Authorization 头；filter 仅在 Bearer 时才解析，自然放行。
 */
@RestController
@RequestMapping("/pay")
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);

    private final AlipayClient alipayClient;
    private final PaymentLogService paymentLogService;
    private final JdbcTemplate jdbc;
    private final UserService userService;
    private final PointsService pointsService;
    private final ObjectMapper json = new ObjectMapper();

    public AlipayNotifyController(AlipayClient alipayClient, PaymentLogService paymentLogService,
                                  JdbcTemplate jdbc, UserService userService, PointsService pointsService) {
        this.alipayClient = alipayClient;
        this.paymentLogService = paymentLogService;
        this.jdbc = jdbc;
        this.userService = userService;
        this.pointsService = pointsService;
    }

    @PostMapping(value = "/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    @Transactional
    public String notify(HttpServletRequest req) {
        Map<String, String> params = parseForm(req);

        // 1) 验签
        if (!alipayClient.verifyNotify(params)) {
            log.warn("[alipay-notify] verify failed params={}", params);
            return "failure";
        }

        // 2) 只处理成功通知
        String tradeStatus = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            // 其它状态（WAIT_BUYER_PAY / TRADE_CLOSED）直接告诉支付宝收到
            return "success";
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmtStr = params.get("total_amount");
        if (outTradeNo == null || totalAmtStr == null) {
            log.warn("[alipay-notify] missing out_trade_no/total_amount params={}", params);
            return "failure";
        }

        // 3) 幂等：已 success 直接返回
        var existing = paymentLogService.findByOutTradeNo(outTradeNo);
        if (existing.isPresent() && "success".equals(existing.get().get("status"))) {
            return "success";
        }

        // 4) 反查订单
        List<Map<String, Object>> orders = jdbc.queryForList(
            "SELECT id, order_no, user_id, pay_amount, status FROM t_order WHERE out_trade_no = ?", outTradeNo);
        if (!orders.isEmpty()) {
            Map<String, Object> od = orders.get(0);
            BigDecimal orderAmt = (BigDecimal) od.get("pay_amount");
            BigDecimal notifyAmt = new BigDecimal(totalAmtStr);
            if (orderAmt.compareTo(notifyAmt) != 0) {
                log.warn("[alipay-notify] amount mismatch order={} notify={}", orderAmt, notifyAmt);
                paymentLogService.log(
                    (String) od.get("order_no"), outTradeNo, tradeNo, "alipay", "notify",
                    notifyAmt, "failed", payloadJson(params));
                return "failure";
            }
            String status = (String) od.get("status");
            if (!"pending_pay".equals(status)) {
                // 已经处理过
                paymentLogService.log(
                    (String) od.get("order_no"), outTradeNo, tradeNo, "alipay", "notify",
                    notifyAmt, "success", payloadJson(params));
                return "success";
            }

            // 5) 业务更新（原子更新，仅首个成功翻 paid 的线程发放消费鱼干，与 /order/pay/status 幂等互斥）
            String orderNo = (String) od.get("order_no");
            int affected = jdbc.update(
                "UPDATE t_order SET status = 'paid', paid_at = NOW(), trade_no = ?, pay_channel = 'alipay' " +
                "WHERE order_no = ? AND status = 'pending_pay'",
                tradeNo, orderNo);
            if (affected > 0) {
                Object buyerObj = od.get("user_id");
                if (buyerObj == null) {
                    log.warn("[alipay-notify] order {} has no user_id, skip points grant", orderNo);
                } else {
                    long buyer = ((Number) buyerObj).longValue();
                    long oid = ((Number) od.get("id")).longValue();
                    try {
                        pointsService.earn(buyer, "consume", notifyAmt.intValue(), "消费奖励", oid);
                    } catch (Exception grantEx) {
                        log.warn("[alipay-notify] consume points grant skipped: {}", grantEx.getMessage());
                    }
                }
            }

            // 6) 写支付日志（success）
            paymentLogService.log(orderNo, outTradeNo, tradeNo, "alipay", "notify",
                notifyAmt, "success", payloadJson(params));
            return "success";
        }

        // 7) 订单未命中 -> 反查会员支付单（会员开通走同一 notify）
        List<Map<String, Object>> vipRows = jdbc.queryForList(
            "SELECT id, user_id, plan_level, amount, status FROM t_vip_pay WHERE out_trade_no = ?", outTradeNo);
        if (!vipRows.isEmpty()) {
            Map<String, Object> vr = vipRows.get(0);
            BigDecimal vipAmt = (BigDecimal) vr.get("amount");
            BigDecimal notifyAmt = new BigDecimal(totalAmtStr);
            if (vipAmt.compareTo(notifyAmt) != 0) {
                log.warn("[alipay-notify] vip amount mismatch vip={} notify={}", vipAmt, notifyAmt);
                paymentLogService.log(null, outTradeNo, tradeNo, "alipay", "notify",
                    notifyAmt, "failed", payloadJson(params));
                return "failure";
            }
            String vstatus = (String) vr.get("status");
            if ("paid".equals(vstatus)) {
                // 已处理过：幂等返回 success
                paymentLogService.log(null, outTradeNo, tradeNo, "alipay", "notify",
                    notifyAmt, "success", payloadJson(params));
                return "success";
            }
            long vuid = ((Number) vr.get("user_id")).longValue();
            int planLevel = ((Number) vr.get("plan_level")).intValue();
            // 开通/续费/升级会员（写入 t_user.vip_level / vip_expire_at）
            userService.grantVip(vuid, planLevel);
            jdbc.update(
                "UPDATE t_vip_pay SET status = 'paid', paid_at = NOW(), trade_no = ? WHERE out_trade_no = ?",
                tradeNo, outTradeNo);
            paymentLogService.log(null, outTradeNo, tradeNo, "alipay", "notify",
                notifyAmt, "success", payloadJson(params));
            return "success";
        }

        // 8) 订单与会员支付单均未命中
        log.warn("[alipay-notify] neither order nor vip_pay found outTradeNo={}", outTradeNo);
        paymentLogService.log(null, outTradeNo, tradeNo, "alipay", "notify",
            new BigDecimal(totalAmtStr), "failed", payloadJson(params));
        return "failure";
    }

    // 将通知参数序列化为合法 JSON（t_payment_log.raw_payload 为 JSON 列，不能用 Map.toString()）
    private String payloadJson(Map<String, String> p) {
        try {
            return json.writeValueAsString(p);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, String> parseForm(HttpServletRequest req) {        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            String v = req.getParameter(n);
            if (v != null) map.put(n, v);
        }
        return map;
    }
}