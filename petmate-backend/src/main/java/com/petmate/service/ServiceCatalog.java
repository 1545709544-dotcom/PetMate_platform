package com.petmate.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务标准分类体系（一级 6 类 + 二级子类）。
 * 对标市面宠物服务平台的目录结构，前后端共用同一套中文枚举，避免商家自由填写造成脏数据。
 * 二级子类顺序即前端 chip 展示顺序。
 */
public final class ServiceCatalog {

    private ServiceCatalog() {}

    /** 一级分类 -> 图标（Material Symbols） */
    public static final Map<String, String> ICONS = new LinkedHashMap<>() {{
        put("美容洗护", "shower");
        put("寄养看护", "cottage");
        put("上门服务", "directions_walk");
        put("行为训练", "school");
        put("宠物医疗", "medical_services");
        put("综合服务", "auto_awesome");
    }};

    /** 一级分类 -> 二级子类 */
    public static final Map<String, List<String>> SUBS = new LinkedHashMap<>() {{
        put("美容洗护", List.of("基础洗浴", "精致洗护", "造型剪毛", "单项护理"));
        put("寄养看护", List.of("日间托管", "短期寄养", "长期寄养"));
        put("上门服务", List.of("上门遛狗", "上门喂养", "上门洗护"));
        put("行为训练", List.of("基础服从", "行为纠正", "幼犬社会化"));
        put("宠物医疗", List.of("疫苗驱虫", "健康体检", "门诊诊疗"));
        put("综合服务", List.of("宠物摄影", "宠物用品定制", "其他服务"));
    }};

    /** 一级分类 -> 计价单位后缀（列表页 "¥128 起/次"） */
    public static final Map<String, String> PRICE_UNITS = new LinkedHashMap<>() {{
        put("美容洗护", "次");
        put("寄养看护", "天");
        put("上门服务", "次");
        put("行为训练", "课时");
        put("宠物医疗", "次");
        put("综合服务", "次");
    }};

    public static boolean isValidCategory(String c) {
        return c != null && SUBS.containsKey(c);
    }

    public static boolean isValidSub(String category, String sub) {
        if (sub == null || sub.isBlank()) return true;
        List<String> subs = SUBS.get(category);
        return subs != null && subs.contains(sub);
    }

    /** 按天计价的分类（寄养需要选入住天数） */
    public static boolean isDailyPriced(String category) {
        return "寄养看护".equals(category);
    }

    public static String icon(String category) {
        return ICONS.getOrDefault(category, "pets");
    }

    public static String priceUnit(String category) {
        return PRICE_UNITS.getOrDefault(category, "次");
    }
}
