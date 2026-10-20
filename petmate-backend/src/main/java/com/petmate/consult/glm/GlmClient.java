package com.petmate.consult.glm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 智谱 GLM-4-Flash 客户端
 *
 * 协议：OpenAI 兼容 Chat Completions（GLM 也支持）
 *   POST {base-url}/chat/completions
 *   Authorization: Bearer {api-key}
 *   body: { model, messages: [{role, content}], temperature, response_format }
 *
 * 用 JDK 11+ java.net.http.HttpClient，不引入额外依赖。
 *
 * 支持两种调用：
 *   - chat()      同步一次性返回完整 content（保留兼容旧调用）
 *   - streamChat() 流式：开启 stream=true，按行解析 SSE，逐片段回调 onChunk
 */
@Component
public class GlmClient {

    private static final Logger log = LoggerFactory.getLogger(GlmClient.class);
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);

    private final AiProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;

    public GlmClient(AiProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 同步（非流式）调用，返回 assistant content。
     */
    public String chat(List<Map<String, Object>> messages) {
        if (blank(props.getApiKey())) {
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI API Key 未配置");
        }
        ObjectNode body = buildBody(messages, false);
        try {
            HttpRequest req = buildRequest(body, Duration.ofMillis(props.getTimeoutMs()));
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return extractContent(resp.statusCode(), resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[glm] error", e);
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 调用异常：" + e.getMessage());
        }
    }

    /**
     * 流式调用：每个 token 片段通过 onChunk 实时回调（前端用于打字机效果）。
     * 解析 text/event-stream：每行 "data: {...}"，[DONE] 结束。
     */
    public void streamChat(List<Map<String, Object>> messages, Consumer<String> onChunk) {
        if (blank(props.getApiKey())) {
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI API Key 未配置");
        }
        ObjectNode body = buildBody(messages, true);
        try {
            HttpRequest req = buildRequest(body, STREAM_TIMEOUT);
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code != 200) {
                String err = readStream(resp.body());
                log.warn("[glm-stream] upstream http {} body={}", code, truncate(err, 300));
                throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 上游 HTTP " + code);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = json.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode c = delta.path("content");
                            if (!c.isMissingNode() && c.isTextual()) {
                                String t = c.asText();
                                if (!t.isEmpty() && onChunk != null) onChunk.accept(t);
                            }
                        }
                    } catch (Exception ignore) {
                        // 跳过无法解析的 SSE 行（心跳/注释/分片残行）
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[glm-stream] io error", e);
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 流式读取异常：" + e.getMessage());
        } catch (Exception e) {
            log.warn("[glm-stream] error", e);
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 流式调用异常：" + e.getMessage());
        }
    }

    private ObjectNode buildBody(List<Map<String, Object>> messages, boolean stream) {
        ObjectNode body = json.createObjectNode();
        boolean vision = messages.stream()
                .anyMatch(m -> m.get("content") instanceof List);
        body.put("model", vision ? props.getVisionModel() : props.getModel());
        body.put("temperature", 0.6);
        if (stream) body.put("stream", true);
        // 纯文本调用启用 JSON mode：智谱 GLM-4-Flash 支持 response_format=json_object，
        // 可强制 AI 输出合法 JSON（字段名由 Prompt 约定）。
        // vision 模型（glm-4v-flash）部分版本不支持该参数，强制不传 → 后端走 markdown 兜底解析。
        if (!vision) {
            ObjectNode rf = json.createObjectNode();
            rf.put("type", "json_object");
            body.set("response_format", rf);
        }
        log.info("[glm] vision={} model={} stream={} jsonMode={}", vision, body.get("model"), stream, !vision);
        ArrayNode msgs = body.putArray("messages");
        for (Map<String, Object> m : messages) {
            ObjectNode mn = msgs.addObject();
            mn.put("role", String.valueOf(m.get("role")));
            Object content = m.get("content");
            if (content instanceof List) {
                // 多模态：content 是 parts 数组（text + image_url）
                ArrayNode parts = mn.putArray("content");
                for (Object part : (List<?>) content) {
                    if (part instanceof Map) {
                        parts.add(json.valueToTree(part));
                    }
                }
            } else {
                mn.put("content", content == null ? "" : String.valueOf(content));
            }
        }
        return body;
    }

    private HttpRequest buildRequest(ObjectNode body, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private String extractContent(int code, String bodyStr) {
        if (code != 200) {
            log.warn("[glm] upstream http {} body={}", code, truncate(bodyStr, 300));
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 上游 HTTP " + code);
        }
        JsonNode root;
        try {
            root = json.readTree(bodyStr);
        } catch (Exception e) {
            log.warn("[glm] invalid json body: {}", truncate(bodyStr, 300));
            throw new BizException(ErrorCode.AI_UPSTREAM_ERROR, "AI 返回解析失败");
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("[glm] no choices: {}", truncate(bodyStr, 300));
            throw new BizException(ErrorCode.AI_RESPONSE_INVALID, "AI 返回无 choices");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isEmpty()) {
            throw new BizException(ErrorCode.AI_RESPONSE_INVALID, "AI 返回空 content");
        }
        return content.asText();
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int n = 0;
            while ((line = br.readLine()) != null && n < 50) {
                sb.append(line).append('\n');
                n++;
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
