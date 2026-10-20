package com.petmate.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局系统配置缓存（平台级开关统一收口）。
 *
 * t_system_config 存放平台级开关，例如紧急熔断 risk.emergency_break（"0"/"1"）。
 * 所有「读开关」的地方都走本服务的内存缓存；管理员写入后调用 refresh()，
 * 缓存立即刷新 —— 无需重启即可全局生效。即「一处开关、全局生效」。
 */
@Service
public class SystemConfigService {

    private final JdbcTemplate jdbc;
    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>(Collections.emptyMap());

    public SystemConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void refresh() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM t_system_config");
            Map<String, String> m = new HashMap<>();
            for (Map<String, Object> r : rows) {
                Object k = r.get("config_key");
                Object v = r.get("config_value");
                if (k != null) m.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
            }
            cache.set(Collections.unmodifiableMap(m));
        } catch (Exception e) {
            // 保持旧缓存，避免单点 DB 抖动导致开关失效
        }
    }

    public String get(String key, String def) {
        String v = cache.get().get(key);
        return v == null ? def : v;
    }

    /** 紧急熔断开关：true = 开启（全局拦截命中敏感词的内容） */
    public boolean isEmergencyBreak() {
        return "1".equals(get("risk.emergency_break", "0"));
    }

    /**
     * 普通用户下单应付运费（单位：元）。
     * VIP 用户（vipLevel>=2 黄金及以上）在订单结算时直接免邮，与本配置无关。
     * 默认 10 元；admin 可在管理后台修改 t_system_config.default_freight 实时生效。
     */
    public java.math.BigDecimal getDefaultFreight() {
        String v = get("default_freight", "10");
        try {
            return new java.math.BigDecimal(v);
        } catch (Exception e) {
            return new java.math.BigDecimal("10");
        }
    }
}
