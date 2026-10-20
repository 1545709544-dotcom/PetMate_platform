package com.petmate.consult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.BizException;
import com.petmate.community.SensitiveWordService;
import com.petmate.consult.glm.AiProperties;
import com.petmate.consult.glm.GlmClient;
import com.petmate.consult.glm.ProductRecommender;
import com.petmate.consult.glm.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问诊 AI 服务
 *
 * v2.0: 接入智谱 GLM-4-Flash（petmate.ai.enabled=true 时）
 * - 四层 prompt：系统指令 / 多轮上下文 / 用户消息
 * - AI 返回末尾 ```json 块：{riskLevel, keywords}
 * - 商品推荐走真实 SKU 池（避免 AI 幻觉）
 * - 敏感词后处理
 * - 失败兜底：返回原 mock 默认回复，调用方可通过 70001 识别降级
 *
 * petmate.ai.enabled=false：维持原关键词 mock（演示态）
 */
@Service
public class ConsultAiService {

    private static final Logger log = LoggerFactory.getLogger(ConsultAiService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{[\\s\\S]*?\\})\\s*```", Pattern.CASE_INSENSITIVE);
    // 兜底：模型可能不按 ```json 包裹，而是裸 JSON / 未闭合的 { 结尾
    private static final Pattern FENCE = Pattern.compile("```");
    private static final Pattern UNFENCED_JSON = Pattern.compile("\\{\\s*\"riskLevel\"", Pattern.DOTALL);
    private static final Pattern TRAILING_BRACE = Pattern.compile("\\{\\s*\"?\\s*$", Pattern.DOTALL);
    // 新版协议：Prompt 要求 AI 在末尾写一行"机器读"标签（中文逗号分隔，不用代码块包裹）
    //   风险等级:normal|urgent|good, 关键词:症状1,症状2,症状3
    // 兼容大小写、允许首尾空格、不强求 "关键词" 一定要有
    private static final Pattern TRAILING_TAG = Pattern.compile(
        "\\s*[\\r\\n]+风险等级\\s*:\\s*(urgent|normal|good)\\s*,?\\s*关键词\\s*:\\s*(.+?)\\s*$",
        Pattern.CASE_INSENSITIVE);

    private final AiProperties aiProps;
    private final GlmClient glmClient;
    private final PromptBuilder promptBuilder;
    private final ProductRecommender productRecommender;
    private final SensitiveWordService sensitive;
    // 可选注入：Redis 未启用时由 Spring 注入 null，缓存层静默跳过
    private final StringRedisTemplate redis;

    public ConsultAiService(AiProperties aiProps,
                            GlmClient glmClient,
                            PromptBuilder promptBuilder,
                            ProductRecommender productRecommender,
                            SensitiveWordService sensitive,
                            @org.springframework.beans.factory.annotation.Autowired(required = false)
                            StringRedisTemplate redis) {
        this.aiProps = aiProps;
        this.glmClient = glmClient;
        this.promptBuilder = promptBuilder;
        this.productRecommender = productRecommender;
        this.sensitive = sensitive;
        this.redis = redis;
        // 启动时打印 AI 真实生效状态，方便排查 enabled/api-key 是否真的进了运行态
        log.info("[ai-boot] enabled={}, provider={}, model={}, baseUrl={}, apiKeyLoaded={}",
                aiProps.isEnabled(),
                aiProps.getProvider(),
                aiProps.getModel(),
                aiProps.getBaseUrl(),
                aiProps.getApiKey() != null && !aiProps.getApiKey().isEmpty() && !aiProps.getApiKey().contains("YOUR_"));
    }

    public static class Recommend {
        public long id;
        public String name;
        public String image;
        public double price;
        public Recommend(long id, String name, String image, double price) {
            this.id = id; this.name = name; this.image = image; this.price = price;
        }
    }

    public static class Reply {
        public String content;
        public String riskLevel;     // urgent / normal / good
        public List<Recommend> products;

        public String productsJson() {
            try { return products == null ? null : JSON.writeValueAsString(products); }
            catch (JsonProcessingException e) { return null; }
        }
    }

    /**
     * 单参版本保留（兼容旧调用），不传 consultationId = 无上下文
     */
    public Reply reply(String content) {
        return reply(content, null);
    }

    /**
     * 主入口。
     * @param content 本轮用户消息
     * @param consultationId 会话 ID；为 null 则无上下文
     */
    public Reply reply(String content, Long consultationId) {
        return reply(content, null, consultationId);
    }

    /**
     * 主入口（带图片）。
     * @param content        本轮用户文本
     * @param images         base64 data URL 列表（前端直传，不落库）；为空/空列表则纯文本
     * @param consultationId 会话 ID；为 null 则无上下文
     */
    public Reply reply(String content, List<String> images, Long consultationId) {
        log.info("[ai-call] enabled={}, cid={}, contentLen={}, imgCount={}",
                aiProps.isEnabled(), consultationId, content == null ? 0 : content.length(),
                images == null ? 0 : images.size());
        if (!aiProps.isEnabled()) {
            log.warn("[ai-call] SKIP to mock because enabled=false");
            return mockReply(content);
        }
        // enabled=true 但 API Key 未配置：直接走知识库兜底，不再假装"AI 服务繁忙"
        // （真实 GLM 调用会在 GlmClient 抛 AI_UPSTREAM_ERROR，误导用户以为服务临时不可用）
        if (aiProps.getApiKey() == null || aiProps.getApiKey().isBlank()) {
            log.warn("[ai-call] SKIP to mock because apiKey not configured (set env ZHIPU_API_KEY to enable real AI)");
            return mockReply(content);
        }
        // 仅缓存"新会话首问"（无 consultationId 且无图片），避免多轮上下文/图片污染缓存
        boolean cacheable = consultationId == null && redis != null
                && (images == null || images.isEmpty());
        if (cacheable) {
            Reply hit = readCache(content);
            if (hit != null) {
                log.info("[ai] cache hit for new consult");
                return hit;
            }
        }
        try {
            Reply r = realReply(content, consultationId, images);
            if (cacheable) writeCache(content, r);
            return r;
        } catch (BizException e) {
            // 上游错误：兜底到 mock
            log.warn("[ai] upstream error, fallback to mock: {}", e.getMessage());
            Reply mock = mockReply(content);
            mock.content = "（AI 服务繁忙，已切换至基础回答）\n\n" + mock.content;
            // 不修改 riskLevel，让前端识别为降级状态
            return mock;
        } catch (Exception e) {
            log.warn("[ai] unexpected error", e);
            return mockReply(content);
        }
    }

    // ============ 缓存层（Redis 不可用时静默） ============

    private static final String CACHE_PREFIX = "petmate:consult:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private String cacheKey(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(content.trim().getBytes(StandardCharsets.UTF_8));
            return CACHE_PREFIX + HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return null; // 算不出 key 就当不可缓存
        }
    }

    private Reply readCache(String content) {
        String key = cacheKey(content);
        if (key == null) return null;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return null;
            return JSON.readValue(json, Reply.class);
        } catch (Exception e) {
            log.warn("[ai] cache read failed (skip): {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String content, Reply r) {
        String key = cacheKey(content);
        if (key == null) return;
        try {
            redis.opsForValue().set(key, JSON.writeValueAsString(r), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[ai] cache write failed (skip): {}", e.getMessage());
        }
    }

    /** 组装发给 LLM 的 messages（含多模态转换：最后一条 user 消息带图片） */
    private List<Map<String, Object>> buildMessages(String content, Long consultationId, List<String> images) {
        List<Map<String, String>> textMsgs = promptBuilder.build(content, consultationId);
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        for (int i = 0; i < textMsgs.size(); i++) {
            Map<String, String> m = textMsgs.get(i);
            if (i == textMsgs.size() - 1 && "user".equals(m.get("role"))
                    && images != null && !images.isEmpty()) {
                // 最后一条用户消息改为多模态：文本 + 图片（base64 data URL）
                java.util.LinkedHashMap<String, Object> mm = new java.util.LinkedHashMap<>();
                mm.put("role", "user");
                java.util.List<java.util.Map<String, Object>> parts = new java.util.ArrayList<>();
                parts.add(java.util.Map.of("type", "text", "text", content));
                for (String img : images) {
                    parts.add(java.util.Map.of("type", "image_url",
                            "image_url", java.util.Map.of("url", img)));
                }
                mm.put("content", parts);
                messages.add(mm);
            } else {
                java.util.LinkedHashMap<String, Object> mm = new java.util.LinkedHashMap<>();
                mm.put("role", m.get("role"));
                mm.put("content", m.get("content"));
                messages.add(mm);
            }
        }
        return messages;
    }

    /** 真实 LLM 调用（同步） */
    private Reply realReply(String content, Long consultationId, List<String> images) {
        String rawContent = glmClient.chat(buildMessages(content, consultationId, images));
        return parseAndBuildReply(rawContent);
    }

    /** 从原始 AI 输出解析文本/riskLevel/keywords，并补齐商品推荐与敏感词处理 */
    private Reply parseAndBuildReply(String rawContent) {
        ParsedBlock parsed = parseAiOutput(rawContent);
        Reply r = new Reply();
        r.content = parsed.text;
        r.riskLevel = parsed.riskLevel;
        r.products = toRecommends(productRecommender.recommend(parsed.riskLevel, parsed.keywords));
        String masked = sensitive.mask(r.content);
        if (!masked.equals(r.content)) {
            r.content = masked;
            r.riskLevel = "normal";
        }
        return r;
    }

    /** 真实可用：enabled 且已配置 api-key */
    private boolean realAiAvailable() {
        return aiProps.isEnabled() && aiProps.getApiKey() != null && !aiProps.getApiKey().isBlank();
    }

    private void emit(Consumer<String> onToken, String text) {
        if (onToken != null && text != null) onToken.accept(text);
    }

    /**
     * 流式调用：逐片段回调 onToken，最终返回完整 Reply（供落库 / 前端 done 事件）。
     * 与 realReply 共享解析、推荐、敏感词与缓存逻辑；mock 兜底同样通过 onToken 回放。
     */
    public Reply streamReply(String content, List<String> images, Long consultationId, Consumer<String> onToken) {
        if (!realAiAvailable()) {
            Reply mock = mockReply(content);
            emit(onToken, mock.content);
            return mock;
        }
        // 仅缓存"新会话首问"（无 consultationId 且无图片）
        boolean cacheable = consultationId == null && redis != null
                && (images == null || images.isEmpty());
        if (cacheable) {
            Reply hit = readCache(content);
            if (hit != null) {
                log.info("[ai] cache hit for new consult (stream)");
                emit(onToken, hit.content);
                return hit;
            }
        }
        try {
            StringBuilder full = new StringBuilder();
            final int[] emitted = {0};
            final boolean[] jsonMode = {false}; // 流式阶段探测到 JSON mode 后走专用路径
            glmClient.streamChat(buildMessages(content, consultationId, images), chunk -> {
                full.append(chunk);
                String acc = full.toString().trim();
                // JSON mode 探测：只要看到前几个字符是 "{" 或 "{"" + 任意字符流，
                // 就锁定为 JSON mode，停止用旧"末尾 JSON 块"剥离逻辑。
                if (!jsonMode[0] && acc.startsWith("{")) {
                    jsonMode[0] = true;
                }
                if (jsonMode[0]) {
                    // JSON mode 不做流式推送——因为中途 JSON 不闭合，
                    // 把半个 JSON 推给前端会出现 {"co... 这种废字符。
                    // 改为流式结束后用同步 chat() 取完整 JSON 一次性推送 content。
                    return;
                }
                // 旧路径：只把 JSON 块之前的正文流式推给前端
                int cut = findJsonStart(full.toString());
                int safeLen = cut >= 0 ? cut : full.length();
                if (safeLen > emitted[0]) {
                    String delta = full.substring(emitted[0], safeLen);
                    emitted[0] = safeLen;
                    emit(onToken, delta);
                }
            });
            Reply r;
            if (jsonMode[0]) {
                // 退化策略：流式接口对 JSON mode 不友好（中间状态非合法 JSON），
                // 改为同步调用一次拿完整 JSON，然后一次性把 content 字段推给前端。
                String fullJson = glmClient.chat(buildMessages(content, consultationId, images));
                r = parseAndBuildReply(fullJson);
                emit(onToken, r.content);
            } else {
                r = parseAndBuildReply(full.toString());
            }
            if (cacheable) writeCache(content, r);
            return r;
        } catch (BizException e) {
            log.warn("[ai] upstream error, fallback to mock: {}", e.getMessage());
            Reply mock = mockReply(content);
            mock.content = "（AI 服务繁忙，已切换至基础回答）\n\n" + mock.content;
            emit(onToken, mock.content);
            return mock;
        } catch (Exception e) {
            log.warn("[ai] unexpected error", e);
            Reply mock = mockReply(content);
            emit(onToken, mock.content);
            return mock;
        }
    }

    /**
     * 定位 AI 输出中风险 JSON 块的起始位置（该位置及其之后视为 JSON，不展示）。
     * 覆盖三种情况：fenced(```)、unfenced({"riskLevel")、流式未闭合的尾部 {。返回 -1 表示无可剥离 JSON。
     */
    private static int findJsonStart(String raw) {
        if (raw == null) return -1;
        Matcher f = FENCE.matcher(raw);
        if (f.find()) return f.start();
        Matcher u = UNFENCED_JSON.matcher(raw);
        if (u.find()) return u.start();
        Matcher t = TRAILING_BRACE.matcher(raw);
        if (t.find()) return t.start();
        return -1;
    }

    /** 从 cut(指向某个 {) 起截取首个完整 {...} 对象，用于解析 riskLevel/keywords */
    private static String extractBraceObject(String raw, int cut) {
        int close = raw.indexOf('}', cut);
        if (close < 0) return null;
        return raw.substring(cut, close + 1);
    }

    private static String pickRisk(String s) {
        return List.of("urgent", "normal", "good").contains(s) ? s : "normal";
    }

    /** 解析 AI 输出：依次尝试 JSON mode / 末尾标签 / 旧 ```json 块 / 裸 JSON 四层兼容 */
    static ParsedBlock parseAiOutput(String raw) {
        ParsedBlock out = new ParsedBlock();
        if (raw == null) {
            out.text = ""; out.riskLevel = "normal"; out.keywords = List.of();
            return out;
        }
        String text = raw;
        String riskLevel = "normal";
        List<String> keywords = new ArrayList<>();

        // 1) JSON mode（response_format=json_object）：整段就是合法 JSON，
        //    其中 content 字段才是真正的 Markdown 正文，riskLevel/keywords 与之并列。
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonNode node = JSON.readTree(trimmed);
                // 兼容多种字段命名（AI 偶有大小写或下划线变体）
                JsonNode contentNode = firstExisting(node, "content", "answer", "text", "markdown");
                if (contentNode != null && contentNode.isTextual()) {
                    String innerContent = contentNode.asText();
                    ParsedBlock inner = parseAiOutput(innerContent); // 递归：内层仍走末尾标签/旧 JSON 解析
                    out.text = inner.text;
                    out.riskLevel = inner.riskLevel;
                    out.keywords = inner.keywords;
                    return out;
                }
            } catch (Exception ignore) {
                // 不是合法 JSON → 进入下面兜底
            }
        }

        // 2) 新版末尾标签："风险等级:normal, 关键词:呕吐,食欲不振"
        Matcher tag = TRAILING_TAG.matcher(text);
        if (tag.find()) {
            riskLevel = pickRisk(tag.group(1).toLowerCase());
            String kwRaw = tag.group(2);
            // 按中文/英文逗号拆分，去掉空白与空项
            for (String k : kwRaw.split("[,，]")) {
                String t = k.trim();
                if (!t.isEmpty()) keywords.add(t);
            }
            // 去掉末尾这行（保留正文）
            text = text.substring(0, tag.start()).trim();
        } else {
            // 3) 兼容旧版 fenced ```json ... ```
            Matcher m = JSON_BLOCK.matcher(raw);
            if (m.find()) {
                text = raw.substring(0, m.start()).trim();
                try {
                    JsonNode node = JSON.readTree(m.group(1));
                    riskLevel = pickRisk(node.path("riskLevel").asText("normal"));
                    JsonNode kw = node.path("keywords");
                    if (kw.isArray()) for (JsonNode n : kw) keywords.add(n.asText());
                } catch (Exception e) {
                    text = raw; // 解析失败则保留原文，避免丢答案
                }
            } else {
                // 4) 兼容旧版裸 JSON 兜底（vision 模型或老 prompt）
                int cut = findJsonStart(raw);
                if (cut >= 0) {
                    text = raw.substring(0, cut).trim();
                    String json = extractBraceObject(raw, cut);
                    if (json != null) {
                        try {
                            JsonNode node = JSON.readTree(json);
                            riskLevel = pickRisk(node.path("riskLevel").asText("normal"));
                            JsonNode kw = node.path("keywords");
                            if (kw.isArray()) for (JsonNode n : kw) keywords.add(n.asText());
                        } catch (Exception ignored) { /* riskLevel 保持 normal */ }
                    }
                }
            }
        }
        // 5) 剥离 prompt 要求模型写的独立"机器读"标签名行（仅标签名，不含其下的"风险等级:..., 关键词:..."行）
        //    后端只用其下的风险等级/关键词提取结构化信息，标签名本身对用户无意义，必须清除。
        //    正则兼容"机器读"与"机器可读"两种写法（"可"在"读"前、可选）。
        text = text.replaceAll("(?im)\\n\\s*机器可?读\\s*$", "").trim();

        out.text = text;
        out.riskLevel = riskLevel;
        out.keywords = keywords;
        return out;
    }

    /** 在 JsonNode 上按顺序查找第一个存在的字段名（用于兼容多种命名） */
    private static JsonNode firstExisting(JsonNode node, String... names) {
        if (node == null) return null;
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && !v.isMissingNode() && !v.isNull()) return v;
        }
        return null;
    }

    /** 把 ProductRecommender.Recommend 转成 ConsultAiService.Recommend（字段对齐前端） */
    private List<Recommend> toRecommends(List<ProductRecommender.Recommend> src) {
        if (src == null) return null;
        List<Recommend> out = new ArrayList<>(src.size());
        for (ProductRecommender.Recommend r : src) {
            out.add(new Recommend(r.id, r.name, r.image, r.price));
        }
        return out;
    }

    static class ParsedBlock {
        String text;
        String riskLevel;
        List<String> keywords;
    }

    // ================= 演示态 mock（petmate.ai.enabled=false 走这里） =================

    private Reply mockReply(String content) {
        String lower = content.toLowerCase();
        Reply r = new Reply();

        if (containsAny(lower, "血", "出血", "抽搐", "昏迷", "中毒", "呼吸困难", "车祸", "摔")) {
            r.riskLevel = "urgent";
            r.content = "⚠️ 您描述的情况可能属于急症，建议：\n1) 立即带宠物前往最近的 24h 宠物医院；\n2) 途中保持宠物安静、保暖；\n3) 如有外伤,用干净纱布按压止血；\n4) 不要自行喂药或喂水。\n\n如需预约医生,可点击下方「立即预约」。";
            r.products = List.of(
                new Recommend(1008, "卫仕 猫用营养膏 120g", "/src/assets/images/consult/prod1-probiotic.jpg", 39.90),
                new Recommend(1009, "谷登 化毛膏 营养补充",   "/src/assets/images/consult/prod2-hairball.jpg", 49.00));
            return r;
        }
        if (containsAny(lower, "吐", "呕吐", "拉稀", "腹泻")) {
            r.riskLevel = "normal";
            r.content = "宠物呕吐/拉稀的常见原因包括:吃得太快、食物变质、肠胃炎、寄生虫、误食等。\n\n建议:\n1) 先禁食 6-8 小时(不禁水);\n2) 观察精神和排便情况;\n3) 如超过 24h 未好转或出现血便,立即就医;\n4) 期间可补充益生菌调理肠胃。\n\n您可以尝试下方推荐产品辅助调理。";
            r.products = List.of(
                new Recommend(1008, "卫仕 猫用营养膏 120g", "/src/assets/images/consult/prod1-probiotic.jpg", 39.90));
            return r;
        }
        if (containsAny(lower, "挑食", "不吃", "食欲")) {
            r.riskLevel = "good";
            r.content = "挑食比较常见,可能原因:\n1) 长期吃零食导致口味挑剔;\n2) 食物气味/温度不吸引;\n3) 季节性食欲下降;\n4) 潜在口腔或肠胃问题。\n\n建议:\n1) 定时定量,30 分钟未吃完即撤走;\n2) 换粮遵循 7 天过渡法;\n3) 适当添加湿粮/罐头增味;\n4) 持续超过 1 周请就医检查。";
            return r;
        }
        if (containsAny(lower, "疫苗", "驱虫")) {
            r.riskLevel = "good";
            r.content = "免疫程序参考:\n- 幼猫 8 周首免,12/16 周加强,以后每年加强一针;\n- 幼犬 6-8 周首免,以后每年加强;\n- 体内驱虫:1 次/月 (6 月龄以下),1 次/3 月 (成犬/成猫)。\n\n建议带宠物去正规医院做一次全面体检,医生会给出更精准的免疫方案。";
            return r;
        }
        if (containsAny(lower, "你好", "您好", "hi", "hello")) {
            r.riskLevel = "good";
            r.content = "您好,我是 PetMate 宠物健康助手 🐾\n请描述宠物目前的具体症状(何时开始、频率、伴随表现等),我会给您一些初步建议。\n\n紧急情况请直接拨打当地 24h 宠物医院电话。";
            return r;
        }
        r.riskLevel = "normal";
        r.content = "感谢您的描述。为了更好地帮助您,请补充:\n1) 宠物种类/品种/年龄;\n2) 症状出现时间和持续时长;\n3) 是否伴随其他异常(精神/食欲/排便);\n4) 最近是否有饮食/环境变化。\n\n您可以发送更详细的描述或图片。";
        return r;
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }
}