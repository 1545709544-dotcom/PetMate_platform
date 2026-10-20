package com.petmate.consult.glm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 真实 SKU 池推荐
 *
 * 不依赖 AI 返回的 SKU id（避免幻觉），由 keywords + riskLevel 在 t_product 中检索。
 * - urgent / normal / good 三档不同关键词权重
 * - 默认按 sale_count DESC LIMIT 20
 * - 返回 1~2 个
 */
@Component
public class ProductRecommender {

    /** 不同 riskLevel 的关键词池 */
    private static final Map<String, List<String>> KEYWORDS = Map.of(
        "urgent", List.of("急救", "营养膏", "益生菌", "营养补充", "补血", "止血"),
        "normal", List.of("肠胃", "益生菌", "止泻", "止吐", "调理", "营养膏", "营养补充"),
        "good",   List.of("营养", "日常", "零食", "罐头", "湿粮", "保健", "维生素")
    );

    /** 每档最多返回几个 */
    private static final Map<String, Integer> LIMITS = Map.of(
        "urgent", 2,
        "normal", 2,
        "good",   1
    );

    private final JdbcTemplate jdbc;

    public ProductRecommender(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param riskLevel urgent / normal / good
     * @param keywords  AI 返回的症状关键词（中文）
     */
    public List<Recommend> recommend(String riskLevel, List<String> keywords) {
        String level = riskLevel == null ? "normal" : riskLevel;
        int limit = LIMITS.getOrDefault(level, 2);

        // 合并风险档关键词 + AI 关键词
        List<String> allKw = new ArrayList<>();
        allKw.addAll(KEYWORDS.getOrDefault(level, KEYWORDS.get("normal")));
        if (keywords != null) allKw.addAll(keywords);

        // 去重（不区分大小写）
        List<String> uniq = new ArrayList<>();
        for (String k : allKw) {
            if (k == null || k.isEmpty()) continue;
            if (uniq.stream().noneMatch(x -> x.equalsIgnoreCase(k))) uniq.add(k);
        }
        if (uniq.isEmpty()) return Collections.emptyList();

        // 拼 LIKE 条件
        StringBuilder where = new StringBuilder(
            "WHERE status = 1 AND is_deleted = 0 AND (");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < uniq.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append("name LIKE ?");
            args.add("%" + uniq.get(i) + "%");
        }
        where.append(") ORDER BY sale_count DESC, id DESC LIMIT ?");
        args.add(limit * 5);  // 取多一些，再过滤重复

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, main_image, sale_price FROM t_product " + where,
            args.toArray());

        List<Recommend> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (out.size() >= limit) break;
            Recommend rec = new Recommend();
            rec.id = ((Number) r.get("id")).longValue();
            rec.name = (String) r.get("name");
            rec.image = (String) r.get("main_image");
            Object price = r.get("sale_price");
            rec.price = price == null ? 0.0 : ((Number) price).doubleValue();
            // 同 id 去重
            if (out.stream().noneMatch(x -> x.id == rec.id)) out.add(rec);
        }
        return out;
    }

    public static class Recommend {
        public long id;
        public String name;
        public String image;
        public double price;
    }
}