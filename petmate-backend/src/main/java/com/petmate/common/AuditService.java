package com.petmate.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 操作审计：谁、在什么时间、对什么模块做了什么操作。
 * 用于商家后台「操作日志」页，满足企业级后台的可追溯要求。
 */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(long merchantId, long operatorId, String operatorName, String role,
                    String module, String action, String target, String detail) {
        if (merchantId <= 0) return;
        jdbc.update(
            "INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
            "VALUES (?,?,?,?,?,?,?,?)",
            merchantId, operatorId, operatorName, role, module, action, target, detail);
    }
}
