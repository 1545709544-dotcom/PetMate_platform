package com.petmate.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.community.SensitiveWordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 紧急熔断全局拦截器。
 *
 * 当 risk.emergency_break = 1 时，对所有「用户内容写入」类请求（POST/PUT/PATCH，非受信任/非交易类路径）
 * 在到达 Controller 之前统一扫描请求体：命中任一敏感词即整体驳回（code 60001）。
 *
 * 与 RequestBodyCacheFilter 配合：拦截器只读取请求体缓存，下游 @RequestBody 仍能读到原始内容（缓存重放）。
 * 兜底：表单/查询参数中的敏感词也会扫描。
 */
@Component
public class EmergencyBreakInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(EmergencyBreakInterceptor.class);
    private static final String BLOCK_MSG = "紧急熔断已开启，命中违规内容，已全局拦截";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemConfigService sysConfig;
    private final SensitiveWordService sensitive;

    public EmergencyBreakInterceptor(SystemConfigService sysConfig, SensitiveWordService sensitive) {
        this.sysConfig = sysConfig;
        this.sensitive = sensitive;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) return true;
        if (!sysConfig.isEmergencyBreak()) return true;

        // 1) 请求体扫描：优先按 JSON 解析并遍历所有字符串字段（自动处理 unicode 转义与任意结构），
        //    非 JSON 则直接对原始文本做敏感词扫描；JSON 树遍历会自动还原转义序列
        String body = readBody(request);
        if (body != null && !body.isEmpty() && containsSensitive(body)) {
            log.warn("[emergency-break] blocked by sensitive word in body, uri={}", request.getRequestURI());
            throw new BizException(ErrorCode.SENSITIVE_WORD, BLOCK_MSG);
        }
        // 2) 兜底：表单/查询参数中的敏感词
        if (containsSensitiveParam(request)) {
            log.warn("[emergency-break] blocked by sensitive param, uri={}", request.getRequestURI());
            throw new BizException(ErrorCode.SENSITIVE_WORD, BLOCK_MSG);
        }
        return true;
    }

    /** 对文本做敏感词命中判断：JSON 则遍历每个字符串叶子，否则整段匹配 */
    private boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                JsonNode root = JSON.readTree(t);
                if (walk(root)) return true;
            } catch (Exception ignored) { /* 非合法 JSON，落到下面的整段匹配 */ }
        }
        return sensitive.contains(text);
    }

    private boolean walk(JsonNode node) {
        if (node == null) return false;
        if (node.isTextual()) return sensitive.contains(node.asText());
        if (node.isObject()) {
            for (JsonNode v : node) if (walk(v)) return true;
        } else if (node.isArray()) {
            for (JsonNode v : node) if (walk(v)) return true;
        }
        return false;
    }

    private boolean containsSensitiveParam(HttpServletRequest request) {
        try {
            for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
                for (String v : e.getValue()) {
                    if (v != null && !v.isEmpty() && sensitive.contains(v)) return true;
                }
            }
        } catch (Exception ignore) { /* 参数不可读时跳过兜底扫描 */ }
        return false;
    }

    private String readBody(HttpServletRequest request) {
        CachedBodyRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, CachedBodyRequestWrapper.class);
        if (wrapper == null) return null;
        byte[] bytes = wrapper.getBody();
        if (bytes == null || bytes.length == 0) return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
