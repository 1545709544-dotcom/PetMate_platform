package com.petmate.track;

import com.petmate.security.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 事件入库服务
 *
 * 把前端 axios 拦截器批量上报的 events 写入 t_event。
 * - 可选鉴权：未登录也允许上报（userId=0）
 * - 单条失败不中断整批
 */
@Service
public class EventIngestService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestService.class);

    private final JdbcTemplate jdbc;

    public EventIngestService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int ingest(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) return 0;
        long uid = TokenContext.get() == null ? 0 : TokenContext.get().userId;
        String role = TokenContext.get() == null ? "guest" : TokenContext.get().role;

        int count = 0;
        for (Map<String, Object> e : events) {
            try {
                jdbc.update(
                    "INSERT INTO t_event " +
                    "(user_id, role, event, page, target_type, target_id, merchant_id, payload, trace_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    uid,
                    role,
                    strOrEmpty(e.get("event")),
                    strOrNull(e.get("page")),
                    strOrNull(e.get("targetType")),
                    longOrNull(e.get("targetId")),
                    longOrZero(e.get("merchantId")),
                    strOrNull(e.get("payload")),
                    strOrNull(e.get("traceId"))
                );
                count++;
            } catch (Exception ex) {
                log.warn("[event] ingest failed: {}", ex.getMessage());
            }
        }
        return count;
    }

    private static String strOrEmpty(Object o) {
        if (o == null) return "";
        return o.toString();
    }

    private static String strOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long longOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private static long longOrZero(Object o) {
        Long v = longOrNull(o);
        return v == null ? 0L : v;
    }
}