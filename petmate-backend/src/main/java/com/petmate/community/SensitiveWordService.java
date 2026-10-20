package com.petmate.community;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 敏感词检测引擎（DFA 实现）。
 *
 * 设计要点：
 * 1. 词库构建为 DFA（确定性有限状态自动机）前缀树，匹配复杂度 O(N) 与词库大小无关。
 * 2. 抗绕过：匹配前对文本做归一化（小写、全角→半角、剥离零宽字符），
 *    匹配时允许"词内插入噪声字符"（空格/标点/特殊符号），即「傻 逼」「傻-逼」也能命中。
 * 3. 词级可配：每个词可设 action（block 拦截 / mask 替换* / review 进审核），命中后由调用方按 action 处置。
 * 4. 命中统计：每次真实检测命中会批量自增 hit_count（best-effort，失败不影响主流程）。
 * 5. 词库变更时由 AdminController 显式调用 refresh() 全量重建 DFA。
 */
@Service
public class SensitiveWordService {

    /** 单个敏感词的元数据 */
    public static class SensitiveWord {
        public final long id;
        public final String word;
        public final String category;
        public final int level;
        public final String action;   // block / mask / review
        public final boolean enabled;

        public SensitiveWord(long id, String word, String category, int level, String action, boolean enabled) {
            this.id = id;
            this.word = word;
            this.category = category;
            this.level = level;
            this.action = action;
            this.enabled = enabled;
        }
    }

    /** 命中结果 */
    public static class Hit {
        public final SensitiveWord meta;   // 命中的词元数据
        public final int start;            // 命中在【原文】中的起始下标（含）
        public final int end;              // 命中在【原文】中的结束下标（含）
        public final String matched;       // 命中片段（原文）

        public Hit(SensitiveWord meta, int start, int end, String matched) {
            this.meta = meta;
            this.start = start;
            this.end = end;
            this.matched = matched;
        }
    }

    /** DFA 节点 */
    private static class DfaNode {
        final Map<Character, DfaNode> children = new HashMap<>();
        boolean isEnd = false;
        SensitiveWord meta = null;

        void add(String normalizedWord, SensitiveWord meta) {
            DfaNode node = this;
            for (char c : normalizedWord.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new DfaNode());
            }
            node.isEnd = true;
            node.meta = meta;
        }
    }

    private final JdbcTemplate jdbc;
    private final AtomicReference<DfaNode> dfa = new AtomicReference<>(new DfaNode());
    private final AtomicReference<List<SensitiveWord>> words = new AtomicReference<>(List.of());

    public SensitiveWordService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        refresh();
    }

    /** 全量重建词库 + DFA（写操作后调用） */
    public synchronized void refresh() {
        List<SensitiveWord> list = jdbc.query(
                "SELECT id, word, category, level, action, enabled FROM t_sensitive_word",
                (rs, i) -> new SensitiveWord(
                        rs.getLong("id"),
                        rs.getString("word"),
                        rs.getString("category"),
                        rs.getInt("level"),
                        rs.getString("action"),
                        rs.getBoolean("enabled")
                ));
        words.set(list);
        DfaNode root = new DfaNode();
        for (SensitiveWord w : list) {
            if (!w.enabled) continue;
            root.add(normalizeWord(w.word), w);
        }
        dfa.set(root);
    }

    public List<SensitiveWord> all() {
        return words.get();
    }

    // ================= 文本归一化 =================

    /** 词库构建用的整词归一化：小写 + 全角→半角（剥离零宽由匹配阶段处理） */
    private static String normalizeWord(String w) {
        StringBuilder sb = new StringBuilder(w.length());
        for (char c : w.toCharArray()) {
            Character n = normalizeChar(c);
            if (n != null) sb.append(n);
        }
        return sb.toString();
    }

    /** 单字符归一化：返回归一化后的字符；零宽字符返回 null（匹配时直接跳过，不计入 DFA 跳转） */
    private static Character normalizeChar(char c) {
        // 零宽字符：直接剥离
        if (c == '\u200b' || c == '\u200c' || c == '\u200d' || c == '\uFEFF' || c == '\u00ad') {
            return null;
        }
        // 全角空格
        if (c == '\u3000') return ' ';
        // 全角 ASCII 变体（！→! … ～→~）
        if (c >= '\uFF01' && c <= '\uFF5E') {
            return (char) (c - 0xFEE0);
        }
        return Character.toLowerCase(c);
    }

    /** 噪声字符：可出现在敏感词内部被跳过（空格/标点/符号/emoji 等），但 CJK 与字母数字不算噪声 */
    private static boolean isNoise(char c) {
        if (Character.isWhitespace(c)) return true;
        boolean isCjk = c >= 0x4E00 && c <= 0x9FFF;
        if (isCjk || Character.isLetterOrDigit(c)) return false;
        return true;
    }

    // ================= 检测核心 =================

    /**
     * 扫描文本，返回所有命中（按原文坐标）。
     * @param bumpCount 是否自增命中统计（真实检测传 true；测试面板传 false 以免污染统计）
     */
    private List<Hit> scan(String text, boolean bumpCount) {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) return hits;
        DfaNode root = dfa.get();
        int n = text.length();
        Set<Long> bumpIds = bumpCount ? new HashSet<>() : null;

        int i = 0;
        while (i < n) {
            DfaNode node = root;
            int j = i;
            int firstMatch = -1;   // 第一个真正命中的原文下标（剔除前导噪声）
            int lastMatch = -1;    // 最后一个真正命中的原文下标
            boolean found = false;
            while (j < n) {
                char c = text.charAt(j);
                Character nc = normalizeChar(c);
                if (nc == null) { j++; continue; }   // 零宽字符：透明跳过，仍计入命中范围
                DfaNode next = node.children.get(nc);
                if (next != null) {
                    node = next;
                    if (firstMatch < 0) firstMatch = j;
                    lastMatch = j;
                    j++;
                    if (node.isEnd) {
                        if (bumpIds != null) bumpIds.add(node.meta.id);
                        hits.add(new Hit(node.meta, firstMatch, lastMatch, text.substring(firstMatch, lastMatch + 1)));
                        found = true;
                        break;
                    }
                } else if (isNoise(c)) {
                    j++;                            // 噪声字符：跳过，继续尝试匹配
                } else {
                    break;                          // 真实字符不匹配：停止本次起点
                }
            }
            // 命中后跳过已命中区间，避免「测试  违禁 词」被重复/重叠命中
            i = found ? lastMatch + 1 : i + 1;
        }
        if (bumpIds != null && !bumpIds.isEmpty()) bumpHitCounts(bumpIds);
        return hits;
    }

    private void bumpHitCounts(Set<Long> ids) {
        try {
            List<Object[]> params = ids.stream().map(id -> new Object[]{id}).collect(Collectors.toList());
            jdbc.batchUpdate("UPDATE t_sensitive_word SET hit_count = hit_count + 1 WHERE id = ?", params);
        } catch (Exception ignore) { /* 统计失败不影响主流程 */ }
    }

    /** 测试面板用：扫描但不污染命中统计 */
    public List<Hit> findAll(String text) {
        return scan(text, false);
    }

    /** 真实检测：扫描并自增命中统计 */
    public List<Hit> detect(String text) {
        return scan(text, true);
    }

    /** 是否包含任意敏感词 */
    public boolean contains(String text) {
        return !detect(text).isEmpty();
    }

    /** 是否命中任意「拦截(block)」类敏感词 */
    public boolean hasBlock(String text) {
        return detect(text).stream().anyMatch(h -> "block".equals(h.meta.action));
    }

    /** 是否命中任意「送审(review)」类敏感词 */
    public boolean hasReview(String text) {
        return detect(text).stream().anyMatch(h -> "review".equals(h.meta.action));
    }

    /** 将命中片段替换成掩码（默认 '*'，保留其余内容） */
    public String mask(String text) {
        return mask(text, '*');
    }

    public String mask(String text, char maskChar) {
        List<Hit> hits = detect(text);
        if (hits.isEmpty()) return text;
        boolean[] covered = new boolean[text.length()];
        for (Hit h : hits) {
            for (int k = h.start; k <= h.end && k < text.length(); k++) covered[k] = true;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int k = 0; k < text.length(); k++) {
            sb.append(covered[k] ? maskChar : text.charAt(k));
        }
        return sb.toString();
    }
}
