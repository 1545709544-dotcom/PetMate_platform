package com.petmate.track;

import com.petmate.common.ApiResp;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 埋点事件入口
 *
 *   POST /track/event
 *   body: { events: [{event, page, targetType, targetId, merchantId, payload, traceId}, ...] }
 *
 * 前端 axios 拦截器批量上报，使用 sendBeacon。
 * 鉴权可选：未登录也可写（userId=0）
 */
@RestController
public class EventController {

    private final EventIngestService ingest;

    public EventController(EventIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/track/event")
    public ApiResp<Map<String, Object>> track(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) return ApiResp.ok(Map.of("accepted", 0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) body.get("events");
        int n = ingest.ingest(events);
        return ApiResp.ok(Map.of("accepted", n));
    }
}