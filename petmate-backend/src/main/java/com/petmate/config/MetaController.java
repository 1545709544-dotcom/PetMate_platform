package com.petmate.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.config.SystemConfigService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「运营配置 / 元数据」类接口集中点。
 *
 * 这些接口对应前端原本写在代码里的硬编码配置（VIP 套餐、搜索热词、热门话题、
 * 知识小贴士、社区活动卡、咨询快捷症状、宠物健康分类、新用户礼包等）。
 *
 * 不要求建新表：表存在则读表（t_topic 等），表为空或字段缺失时回退到
 * 服务端常量，确保前端始终能拿到一份可用数据。
 */
@RestController
public class MetaController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final SystemConfigService sysConfig;

    public MetaController(JdbcTemplate jdbc, SystemConfigService sysConfig) {
        this.jdbc = jdbc;
        this.sysConfig = sysConfig;
    }

    // ============ VIP 套餐（VipOpenView + ProfileHubView）============
    @GetMapping("/vip/plan")
    public ApiResp<Map<String, Object>> vipPlan() {
        List<Map<String, Object>> compare = new ArrayList<>();
        compare.add(compareRow("AI 问诊记录", "保留 30 天", "长期保存", "长期保存"));
        compare.add(compareRow("商城运费", "10 元", "免邮", "免邮 + 95 折"));
        compare.add(compareRow("生日礼包", "—", "√", "√"));
        // 历史表里保留「客服支持/线下门店/免费体检」等已下架权益，已不在套餐承诺范围内
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plans", readVipPlans());
        out.put("compareRows", compare);
        return ApiResp.ok(out);
    }

    /**
     * VIP 套餐：优先读 t_system_config.vip.plans（管理端可改价），解析失败/缺失则回退服务端常量。
     */
    private List<Map<String, Object>> readVipPlans() {
        String raw = sysConfig.get("vip.plans", "");
        if (!raw.isEmpty()) {
            try {
                List<Map<String, Object>> parsed = JSON.readValue(raw,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (parsed != null && !parsed.isEmpty()) return parsed;
            } catch (Exception ignore) {}
        }
        // 兜底常量（与 t_system_config 种子一致）
        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(plan(1, "L1", "白银会员", 18, "新晋福利",
                Arrays.asList("AI 问诊记录长期保存", "商城购物免邮", "生日礼包", "每月 1 张满减券")));
        plans.add(plan(2, "L2", "黄金会员", 29, "人气之选",
                Arrays.asList("AI 问诊记录长期保存", "商城购物免邮", "生日礼包", "每月 1 张满减券")));
        plans.add(plan(3, "L3", "钻石会员", 98, "全权益",
                Arrays.asList("AI 问诊记录长期保存", "商城购物免邮 + 95 折", "生日礼包", "每月 2 张满减券")));
        return plans;
    }

    private static Map<String, Object> plan(int level, String tag, String name, int price, String badge, List<String> perks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", tag.toLowerCase());     // 兼容前端按 key 选中套餐
        m.put("level", level);
        m.put("tag", tag);
        m.put("name", name);
        m.put("monthly", price);
        m.put("price", price);
        m.put("badge", badge);
        m.put("perks", perks);
        return m;
    }

    private static Map<String, Object> compareRow(String feature, String desc, String basic, String hasVip) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feature", feature);
        m.put("desc", desc);
        m.put("basic", basic);
        m.put("hasVip", hasVip);
        return m;
    }

    // ============ 知识小贴士（KnowledgeListView 侧栏轮播）============
    @GetMapping("/knowledge/tip")
    public ApiResp<Map<String, Object>> knowledgeTip() {
        List<Map<String, Object>> tips = new ArrayList<>();
        tips.add(tip("🐱", "猫咪舔毛", "猫咪舔毛不只是清洁，还能缓解压力，频繁过度需排查皮肤问题。"));
        tips.add(tip("🐶", "狗狗摇尾", "摇尾巴不一定代表开心——夹尾低摇往往是紧张或不安的信号。"));
        tips.add(tip("🐾", "幼宠疫苗", "幼宠 6-8 周首免，之后每 2-4 周加强，狂犬单苗 12 周后接种。"));
        tips.add(tip("💧", "饮水机", "猫天性爱喝流动水，自动饮水机可以显著提高猫咪饮水量。"));
        tips.add(tip("🦴", "驱虫频率", "体外驱虫每月 1 次，体内驱虫 3 个月 1 次（家养可适当放宽）。"));
        tips.add(tip("🌡", "中暑信号", "夏季遛狗避开正午，地面烫手请改清晨或傍晚；气喘不止立即降温。"));
        tips.add(tip("🦷", "口腔护理", "猫狗 3 岁后 80% 出现牙结石，每周刷牙 2-3 次最经济有效。"));
        tips.add(tip("🛁", "洗澡频率", "猫无需频繁洗澡（每 2-3 月一次即可），过度洗澡会破坏皮脂。"));
        tips.add(tip("🐇", "兔子饮食", "兔主食是牧草，蔬菜限量，零食瓜果要少，否则易消化不良。"));
        tips.add(tip("🦜", "鹦鹉补钙", "墨鱼骨挂在笼内可补钙又磨喙，比保健砂更天然。"));
        tips.add(tip("🐢", "乌龟晒背", "乌龟每周晒背 2-3 次，每次 30 分钟，UVB 灯可代替阳光。"));
        tips.add(tip("💊", "人药勿喂", "布洛芬、对乙酰氨基酚对猫狗剧毒，宠物体质差异大，务必用宠物专用药。"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tips", tips);
        return ApiResp.ok(out);
    }

    private static Map<String, Object> tip(String emoji, String title, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("emoji", emoji);
        m.put("title", title);
        m.put("text", text);
        return m;
    }

    // ============ 搜索热词（SearchView）============
    @GetMapping("/search/hot-words")
    public ApiResp<List<String>> hotWords() {
        // 演示版：固定列表。生产可改为统计近 N 天搜索日志的高频词。
        return ApiResp.ok(Arrays.asList("猫粮", "狗粮", "饮水机", "玩具", "猫窝", "驱虫"));
    }

    // ============ 社区活动卡（CommunityView 侧栏）============
    @GetMapping("/community/activities")
    public ApiResp<List<Map<String, Object>>> communityActivities() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> donate = new LinkedHashMap<>();
        donate.put("title", "爱心捐助榜");
        donate.put("desc", "捐助排行 · 爱心奖励");
        donate.put("to", "/community/donate-rank");
        donate.put("variant", "donate");
        donate.put("icon", "favorite");
        donate.put("cover", "/static/uploads/activity/donate.jpg");
        out.add(donate);
        Map<String, Object> photo = new LinkedHashMap<>();
        photo.put("title", "宠物摄影大赛");
        photo.put("desc", "发帖带 #宠物摄影大赛 即参赛 · 按点赞排名");
        photo.put("to", "/community/photo-contest");
        photo.put("variant", "photo");
        photo.put("icon", "photo_camera");
        photo.put("cover", "/static/uploads/activity/photo.jpg");
        out.add(photo);
        return ApiResp.ok(out);
    }

    // ============ 咨询快捷症状（ConsultView 顶部 chips）============
    @GetMapping("/consult/quick-symptoms")
    public ApiResp<List<Map<String, Object>>> quickSymptoms() {
        List<Map<String, Object>> out = new ArrayList<>();
        addSymptom(out, "frequent_vomit", "频繁呕吐", "我家宠物最近总是呕吐，怎么办？");
        addSymptom(out, "lethargy", "精神萎靡", "宠物最近精神不太好，不爱动也不吃东西。");
        addSymptom(out, "soft_stool", "软便/拉稀", "宠物出现软便或拉稀，请帮我看看。");
        addSymptom(out, "poor_appetite", "食欲不振", "宠物最近食欲下降，吃得很少。");
        addSymptom(out, "skin_problem", "皮肤问题", "宠物最近掉毛严重，皮肤有红点。");
        addSymptom(out, "cough_sneeze", "咳嗽打喷嚏", "宠物最近咳嗽，偶尔打喷嚏。");
        return ApiResp.ok(out);
    }

    private static void addSymptom(List<Map<String, Object>> list, String key, String label, String prompt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("prompt", prompt);
        list.add(m);
    }

    // ============ 宠物健康记录分类（PetDetailView）============
    @GetMapping("/pet/health-categories")
    public ApiResp<List<Map<String, Object>>> healthCategories() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(category("vaccine", "疫苗", "vaccines", "orange"));
        out.add(category("deworming", "驱虫", "pest_control", "green"));
        out.add(category("checkup", "体检", "health_and_safety", "blue"));
        out.add(category("weight", "体重", "monitor_weight", "purple"));
        out.add(category("illness", "病历", "medication", "red"));
        return ApiResp.ok(out);
    }

    private static Map<String, Object> category(String key, String label, String icon, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("icon", icon);
        m.put("color", color);
        return m;
    }

    // ============ 新用户大礼包券列表（ProductListView 一键领取）============
    @GetMapping("/coupon/gift-pack")
    public ApiResp<List<Map<String, Object>>> giftPack(
            @RequestParam(defaultValue = "10001") long templateId) {
        // 优先读 t_system_config.coupon.gift_pack（管理端可改面额/门槛），缺失则回退服务端常量。
        String raw = sysConfig.get("coupon.gift_pack", "");
        if (!raw.isEmpty()) {
            try {
                List<Map<String, Object>> parsed = JSON.readValue(raw,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (parsed != null && !parsed.isEmpty()) return ApiResp.ok(parsed);
            } catch (Exception ignore) {}
        }
        // 兜底常量（与 t_system_config 种子一致）
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(gift(1, "新人 10 元券", 10, 0));
        out.add(gift(2, "新人 30 元券", 30, 99));
        out.add(gift(3, "新人 50 元券", 50, 199));
        return ApiResp.ok(out);
    }

    private static Map<String, Object> gift(int id, String name, int amount, int threshold) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("amount", amount);
        m.put("threshold", threshold);
        return m;
    }
}