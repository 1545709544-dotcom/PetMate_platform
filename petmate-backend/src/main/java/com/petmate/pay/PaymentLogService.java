package com.petmate.pay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支付日志服务（对账）
 *
 * t_payment_log 表作为幂等键 + 审计日志：
 *  - uk_out_trade_no UNIQUE 索引天然防重复通知
 *  - action: precreate / notify / refund
 *  - status: pending / success / failed
 */
@Service
public class PaymentLogService {

    private final JdbcTemplate jdbc;

    public PaymentLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(String orderNo, String outTradeNo, String tradeNo, String channel,
                    String action, java.math.BigDecimal amount, String status, String rawJson) {
        jdbc.update(
            "INSERT IGNORE INTO t_payment_log " +
            "(order_no, out_trade_no, trade_no, channel, action, amount, status, raw_payload) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            orderNo, outTradeNo, tradeNo, channel, action, amount, status, rawJson);
    }

    public Optional<Map<String, Object>> findByOutTradeNo(String outTradeNo) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_no, out_trade_no, trade_no, channel, action, amount, status, raw_payload, created_at " +
            "FROM t_payment_log WHERE out_trade_no = ? ORDER BY id DESC LIMIT 1", outTradeNo);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}