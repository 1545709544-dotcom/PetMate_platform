package com.petmate.consult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
public class ConsultController {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 普通用户 AI 问诊次数上限（按用户提问条数计，每条提问 = 1 次） */
    private static final int MAX_FREE_CONSULTATIONS = 10;
    /** 额度用尽时的提示文案 */
    private static final String QUOTA_MSG =
        "您已达 AI 问诊使用上限（普通用户最多 " + MAX_FREE_CONSULTATIONS + " 次），可开通 VIP 享受无限次问诊";

    private final JdbcTemplate jdbc;
    private final ConsultAiService ai;

    public ConsultController(JdbcTemplate jdbc, ConsultAiService ai) {
        this.jdbc = jdbc;
        this.ai = ai;
    }

    @PostMapping("/consult/send")
    public ApiResp<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String content = (String) body.get("content");
        Object rawImages = body.get("images");
        boolean hasImages = rawImages instanceof List && !((List<?>) rawImages).isEmpty();
        if (content == null) content = "";
        else content = content.trim();
        // content 与 images 至少一个非空
        if (content.isEmpty() && !hasImages) throw new BizException(ErrorCode.PARAM_ERROR, "请填写内容或上传图片");

        // 图片（base64 data URL 列表，前端直传，不落库；用于多模态问诊）
        List<String> images = null;
        Object imgObj = body.get("images");
        if (imgObj instanceof List) {
            images = ((List<?>) imgObj).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        }

        Long consultationId = body.get("consultationId") == null ? null : ((Number) body.get("consultationId")).longValue();
        Long petId = body.get("petId") == null ? null : ((Number) body.get("petId")).longValue();

        // 额度校验：按「用户提问次数」统计，每次发送（每条 user 提问）消耗 1 次额度
        // 普通用户达到上限即拦截；管理员/商家/VIP 免限额（isPrivileged）
        boolean willCreate = (consultationId == null);
        if (!willCreate) {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_consultation WHERE id = ? AND user_id = ?", Integer.class, consultationId, uid);
            if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "问诊不存在");
        }
        if (!isPrivileged(uid) && countUsedConsultations(uid) >= MAX_FREE_CONSULTATIONS) {
            throw new BizException(ErrorCode.CONSULT_QUOTA_EXCEEDED, QUOTA_MSG);
        }

        if (willCreate) {
            // 新建
            String title = content.length() > 30 ? content.substring(0, 30) : content;
            jdbc.update(
                "INSERT INTO t_consultation (user_id, pet_id, title, status, risk_level, message_count) " +
                "VALUES (?, ?, ?, 'active', 'normal', 0)",
                uid, petId, title);
            consultationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }

        // 存 user 消息（含上传图片，base64 直传落库）
        long userMessageId = insertUserMessage(consultationId, content, images);
        jdbc.update("UPDATE t_consultation SET message_count = message_count + 1 WHERE id = ?", consultationId);

        // AI 回复（v2.0：传入 consultationId 拉多轮上下文；AI enabled 时走真实 GLM，失败 fallback 到 mock）
        ConsultAiService.Reply reply = ai.reply(content, images, consultationId);

        jdbc.update(
            "INSERT INTO t_consultation_message (consultation_id, role, content, risk_level, products) " +
            "VALUES (?, 'assistant', ?, ?, ?)",
            consultationId, reply.content, reply.riskLevel,
            reply.products == null ? null : reply.productsJson());
        jdbc.update("UPDATE t_consultation SET message_count = message_count + 1, risk_level = ? WHERE id = ?",
                reply.riskLevel, consultationId);
        long messageId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", messageId);
        resp.put("consultationId", consultationId);
        resp.put("userMessageId", userMessageId);
        resp.put("role", "assistant");
        resp.put("content", reply.content);
        resp.put("riskLevel", reply.riskLevel);
        resp.put("products", reply.products);
        resp.put("createdAt", LocalDateTime.now().toString());
        return ApiResp.ok(resp);
    }

    /**
     * 流式问诊（SSE）。
     * 前端通过 fetch 读取 text/event-stream，实时拼装 AI 回复，体验更顺滑。
     * 事件协议：
     *   event: token  data: {"chunk":"...文本片段..."}
     *   event: done   data: {"id":...,"consultationId":...,"userMessageId":...,"riskLevel":...,"products":[...],"createdAt":"..."}
     *   event: error  data: {"message":"..."}
     */
    @PostMapping(value = "/consult/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendStream(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String content = (String) body.get("content");
        Object rawImages = body.get("images");
        boolean hasImages = rawImages instanceof List && !((List<?>) rawImages).isEmpty();
        if (content == null) content = "";
        else content = content.trim();
        // content 与 images 至少一个非空
        if (content.isEmpty() && !hasImages) throw new BizException(ErrorCode.PARAM_ERROR, "请填写内容或上传图片");

        // 图片（base64 data URL 列表）
        List<String> images = null;
        Object imgObj = body.get("images");
        if (imgObj instanceof List) {
            images = ((List<?>) imgObj).stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        }

        Long consultationId = body.get("consultationId") == null ? null : ((Number) body.get("consultationId")).longValue();
        Long petId = body.get("petId") == null ? null : ((Number) body.get("petId")).longValue();

        // 额度校验：按「用户提问次数」统计，每次发送（每条 user 提问）消耗 1 次额度
        // 普通用户达到上限即拦截；管理员/商家/VIP 免限额（isPrivileged）
        boolean willCreate = (consultationId == null);
        if (!willCreate) {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_consultation WHERE id = ? AND user_id = ?", Integer.class, consultationId, uid);
            if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "问诊不存在");
        }
        if (!isPrivileged(uid) && countUsedConsultations(uid) >= MAX_FREE_CONSULTATIONS) {
            // SSE 端点不能抛异常（否则前端按 SSE 解析会吞掉消息），
            // 改为立即发送 error 事件并结束流，前端 onError 可拿到清晰提示
            return quotaExceededEmitter();
        }

        if (willCreate) {
            // 新建
            String title = content.length() > 30 ? content.substring(0, 30) : content;
            jdbc.update(
                "INSERT INTO t_consultation (user_id, pet_id, title, status, risk_level, message_count) " +
                "VALUES (?, ?, ?, 'active', 'normal', 0)",
                uid, petId, title);
            consultationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }

        // 同步存 user 消息（含上传图片），保证落库与多轮上下文一致
        long userMessageId = insertUserMessage(consultationId, content, images);
        jdbc.update("UPDATE t_consultation SET message_count = message_count + 1 WHERE id = ?", consultationId);

        final long cid = consultationId;
        final long uMid = userMessageId;
        final List<String> imgs = images;
        final String text = content;

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError((e) -> emitter.complete());

        // 异步跑流式 AI，避免阻塞请求线程；SSE 连接立即返回
        CompletableFuture.runAsync(() -> {
            try {
                ConsultAiService.Reply reply = ai.streamReply(text, imgs, cid, chunk -> {
                    try {
                        if (chunk != null && !chunk.isEmpty()) {
                            emitter.send(SseEmitter.event().name("token")
                                .data(java.util.Map.of("chunk", chunk)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                // 落库助手消息
                jdbc.update(
                    "INSERT INTO t_consultation_message (consultation_id, role, content, risk_level, products) " +
                    "VALUES (?, 'assistant', ?, ?, ?)",
                    cid, reply.content, reply.riskLevel,
                    reply.products == null ? null : reply.productsJson());
                jdbc.update("UPDATE t_consultation SET message_count = message_count + 1, risk_level = ? WHERE id = ?",
                        reply.riskLevel, cid);
                long messageId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                java.util.Map<String, Object> done = new java.util.LinkedHashMap<>();
                done.put("id", messageId);
                done.put("consultationId", cid);
                done.put("userMessageId", uMid);
                done.put("riskLevel", reply.riskLevel);
                done.put("products", reply.products);
                done.put("createdAt", LocalDateTime.now().toString());
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(java.util.Map.of(
                        "message", e.getMessage() == null ? "AI 服务异常" : e.getMessage())));
                } catch (IOException ignore) {
                    // 客户端已断开，忽略
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    /** 统计用户已发起的问诊提问次数（按 user 消息条数计，每条提问消耗 1 次额度） */
    private int countUsedConsultations(long uid) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_consultation_message m " +
            "JOIN t_consultation c ON c.id = m.consultation_id " +
            "WHERE c.user_id = ? AND c.is_deleted = 0 AND m.role = 'user'",
            Integer.class, uid);
        return c == null ? 0 : c;
    }

    /** 是否免限额：管理员 / 商家 / 处于有效期内的 VIP */
    private boolean isPrivileged(long uid) {
        try {
            Map<String, Object> u = jdbc.queryForMap(
                "SELECT role, vip_level, vip_expire_at FROM t_user WHERE id = ?", uid);
            String role = String.valueOf(u.get("role"));
            if (!"user".equals(role)) return true; // admin / merchant 等免限额
            Object lvl = u.get("vip_level");
            Object exp = u.get("vip_expire_at");
            int level = lvl == null ? 0 : ((Number) lvl).intValue();
            if (level > 0 && exp != null) {
                LocalDateTime expire = toLocalDateTime(exp);
                if (expire != null && expire.isAfter(LocalDateTime.now())) return true;
            }
            return false;
        } catch (Exception e) {
            // 取不到用户信息时按普通用户处理（保守），不影响主流程
            return false;
        }
    }

    /** 兼容不同类型的时间列（Timestamp / Date / 字符串） */
    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toLocalDateTime();
        if (o instanceof java.sql.Date) return ((java.sql.Date) o).toLocalDate().atStartOfDay();
        if (o instanceof String) {
            try {
                return LocalDateTime.parse((String) o,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    /** 额度用尽时，SSE 端点返回一条 error 事件（携带清晰提示）后结束流 */
    private SseEmitter quotaExceededEmitter() {
        SseEmitter e = new SseEmitter(30_000L);
        try {
            e.send(SseEmitter.event().name("error")
                .data(java.util.Map.of("message", QUOTA_MSG, "type", "quota")));
        } catch (IOException ignore) {
            // 客户端已断开，忽略
        }
        e.complete();
        return e;
    }

    @GetMapping("/consult/quota")
    public ApiResp<Map<String, Object>> quota() {
        long uid = TokenContext.requireUserId();
        int used = countUsedConsultations(uid);
        boolean unlimited = isPrivileged(uid);
        int remaining = unlimited ? -1 : Math.max(0, MAX_FREE_CONSULTATIONS - used);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", MAX_FREE_CONSULTATIONS);
        data.put("used", used);
        data.put("remaining", remaining);
        data.put("unlimited", unlimited);
        return ApiResp.ok(data);
    }

    private long insertUserMessage(long consultationId, String content, List<String> images) {
        String imagesJson = null;
        if (images != null && !images.isEmpty()) {
            try { imagesJson = JSON.writeValueAsString(images); } catch (Exception ignore) {}
        }
        jdbc.update(
            "INSERT INTO t_consultation_message (consultation_id, role, content, images) VALUES (?, 'user', ?, ?)",
            consultationId, content, imagesJson);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 问诊历史列表（统一返回 camelCase，与前端 Consultation 类型对齐）
     *
     * @param range  today / 7d / 30d / all （默认 all 兼容旧调用）
     */
    @GetMapping("/consult/list")
    public ApiResp<List<Map<String, Object>>> history(
            @RequestParam(required = false, defaultValue = "all") String range) {
        long uid = TokenContext.requireUserId();
        // 只返回真正说过话的会话：至少存在一条 user 消息（message_count>0 且有用户发言）
        StringBuilder sql = new StringBuilder(
            "SELECT id, pet_id, title, status, risk_level, message_count, created_at " +
            "FROM t_consultation WHERE user_id = ? AND is_deleted = 0 AND message_count > 0");
        Object[] args;
        if ("today".equalsIgnoreCase(range)) {
            sql.append(" AND created_at >= ? ORDER BY id DESC");
            args = new Object[]{uid, Timestamp.valueOf(LocalDate.now().atStartOfDay())};
        } else if ("7d".equalsIgnoreCase(range)) {
            sql.append(" AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) ORDER BY id DESC");
            args = new Object[]{uid};
        } else if ("30d".equalsIgnoreCase(range)) {
            sql.append(" AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) ORDER BY id DESC");
            args = new Object[]{uid};
        } else {
            sql.append(" ORDER BY id DESC");
            args = new Object[]{uid};
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args);

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("petId", r.get("pet_id"));
            m.put("title", r.get("title"));
            m.put("status", r.get("status"));
            m.put("riskLevel", r.get("risk_level"));
            m.put("messageCount", r.get("message_count"));
            m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());

            // 首条 user 消息摘要
            long cid = ((Number) r.get("id")).longValue();
            String preview = "";
            try {
                List<Map<String, Object>> first = jdbc.queryForList(
                    "SELECT content FROM t_consultation_message " +
                    "WHERE consultation_id = ? AND role = 'user' ORDER BY id ASC LIMIT 1", cid);
                if (!first.isEmpty()) {
                    String s = String.valueOf(first.get(0).get("content"));
                    preview = s.length() > 40 ? s.substring(0, 40) + "…" : s;
                }
            } catch (Exception ignore) {}
            m.put("preview", preview);
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    @GetMapping("/consult/{id}/messages")
    public ApiResp<List<Map<String, Object>>> detail(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_consultation WHERE id = ? AND user_id = ?", Integer.class, id, uid);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "问诊不存在");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, consultation_id, role, content, risk_level, products, images, created_at " +
            "FROM t_consultation_message WHERE consultation_id = ? ORDER BY id ASC", id);

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("consultationId", r.get("consultation_id"));
            m.put("role", r.get("role"));
            m.put("content", r.get("content"));
            m.put("riskLevel", r.get("risk_level"));
            m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());

            // products JSON 字段在 MySQL 中是 String；前端期望对象数组，反序列化后返回
            Object raw = r.get("products");
            if (raw == null) {
                m.put("products", null);
            } else {
                String s = String.valueOf(raw);
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                    m.put("products", null);
                } else {
                    try {
                        List<Map<String, Object>> list = JSON.readValue(s,
                            new TypeReference<List<Map<String, Object>>>() {});
                        m.put("products", list);
                    } catch (Exception e) {
                        m.put("products", null);
                    }
                }
            }

            // 用户上传图片（base64 data URL JSON 数组），无图则为 null
            Object rawImages = r.get("images");
            if (rawImages == null) {
                m.put("images", null);
            } else {
                String s = String.valueOf(rawImages);
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                    m.put("images", null);
                } else {
                    try {
                        List<String> list = JSON.readValue(s, new TypeReference<List<String>>() {});
                        m.put("images", list);
                    } catch (Exception e) {
                        m.put("images", null);
                    }
                }
            }

            out.add(m);
        }
        return ApiResp.ok(out);
    }

    @PostMapping("/consult/create")
    public ApiResp<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Long petId = body == null || body.get("petId") == null ? null : ((Number) body.get("petId")).longValue();
        jdbc.update(
            "INSERT INTO t_consultation (user_id, pet_id, title, status, risk_level, message_count) " +
            "VALUES (?, ?, '新问诊', 'active', 'normal', 0)",
            uid, petId);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResp.ok(Map.of("id", id, "userId", uid, "petId", petId == null ? 0 : petId,
                "title", "新问诊", "status", "active", "riskLevel", "normal",
                "messageCount", 0, "createdAt", LocalDateTime.now().toString()));
    }

    /** 删除问诊会话（软删除，校验所有权） */
    @DeleteMapping("/consult/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        int rows = jdbc.update(
            "UPDATE t_consultation SET is_deleted = 1 WHERE id = ? AND user_id = ?", id, uid);
        if (rows == 0) throw new BizException(ErrorCode.PARAM_ERROR, "问诊不存在");
        return ApiResp.ok();
    }

    /** 删除某条用户消息里指定索引的图片（永久删除，校验所有权） */
    @DeleteMapping("/consult/message/{messageId}/image")
    public ApiResp<Map<String, Object>> deleteMessageImage(
            @PathVariable long messageId, @RequestParam int index) {
        long uid = TokenContext.requireUserId();
        // 所有权校验：message -> consultation -> user，且必须是 user 消息
        Integer owner = jdbc.queryForObject(
            "SELECT c.user_id FROM t_consultation_message m " +
            "JOIN t_consultation c ON c.id = m.consultation_id " +
            "WHERE m.id = ? AND m.role = 'user' AND c.user_id = ? AND c.is_deleted = 0",
            Integer.class, messageId, uid);
        if (owner == null) throw new BizException(ErrorCode.PARAM_ERROR, "消息不存在");

        String raw = jdbc.queryForObject(
            "SELECT images FROM t_consultation_message WHERE id = ?", String.class, messageId);
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return ApiResp.ok(Map.of("ok", true));
        }
        try {
            List<String> list = JSON.readValue(raw, new TypeReference<List<String>>() {});
            if (index < 0 || index >= list.size()) {
                return ApiResp.ok(Map.of("ok", true));
            }
            list.remove(index);
            String newJson = list.isEmpty() ? null : JSON.writeValueAsString(list);
            jdbc.update("UPDATE t_consultation_message SET images = ? WHERE id = ?", newJson, messageId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片数据解析失败");
        }
        return ApiResp.ok(Map.of("ok", true));
    }
}
