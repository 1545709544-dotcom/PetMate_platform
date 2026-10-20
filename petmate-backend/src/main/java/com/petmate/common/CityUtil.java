package com.petmate.common;

/**
 * 城市名归一化工具。
 *
 * <p>背景：社区「同城」筛选在 SQL 里是 {@code AND city = ?} 精确匹配，
 * 但 city 的来源有三处且格式各不相同：
 * <ul>
 *   <li>个人资料地区级联器 —— 写入 {@code "上海市/上海市/徐汇区"} 整串</li>
 *   <li>发帖接口 body.city —— 前端传什么落什么，出现过 {@code "北京"} / {@code "北京市"}</li>
 *   <li>历史种子数据 —— {@code "驻马店"} 之类的裸城市名</li>
 * </ul>
 * 结果是同城帖子互相看不见。此工具把上述形态统一收敛为「不带行政后缀的短名」，
 * 例如 {@code 上海市/上海市/徐汇区 -> 上海}、{@code 北京市 -> 北京}、{@code 驻马店市 -> 驻马店}。
 *
 * <p>写入端（发帖、改资料）与查询端（feed 过滤）都必须调用，否则又会分叉。
 */
public final class CityUtil {

    private CityUtil() {}

    /** 需要剥离的行政区划后缀，长后缀在前，避免「特别行政区」被「区」先截断。 */
    private static final String[] SUFFIXES = {
            "特别行政区", "维吾尔自治区", "回族自治区", "壮族自治区", "自治区",
            "自治州", "地区", "盟", "市", "县",
    };

    /**
     * 归一化城市名。
     *
     * @param raw 原始值，可能是 {@code null}、空串、单段城市名，或 {@code 省/市/区} 路径
     * @return 归一化后的短名；入参为空时返回 {@code null}（便于直接写库存 NULL）
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // 兼容 "省/市/区" 与 "省 市 区" 两种分隔；取「市」那一段（第 2 段），
        // 只有一段时就用它本身（直辖市场景第 1、2 段相同，取哪段都对）。
        String[] parts = s.split("[/\\s]+");
        String city = parts.length >= 2 ? parts[1] : parts[0];
        city = city.trim();
        if (city.isEmpty()) return null;

        for (String suffix : SUFFIXES) {
            if (city.length() > suffix.length() && city.endsWith(suffix)) {
                city = city.substring(0, city.length() - suffix.length());
                break;
            }
        }
        return city.isEmpty() ? null : city;
    }
}
