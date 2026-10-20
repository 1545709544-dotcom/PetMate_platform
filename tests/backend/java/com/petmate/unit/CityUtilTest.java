package com.petmate.unit;

import com.petmate.common.CityUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 · 城市名归一化。
 *
 * <p>被测：{@code com.petmate.common.CityUtil}
 *
 * <p>社区「同城」筛选在 SQL 里是 {@code AND city = ?} 精确匹配，
 * 而 city 有三个来源、三种格式（省市区整串 / 带后缀 / 裸城市名）。
 * 归一化一旦分叉，同城帖子就互相看不见 —— 是典型的「不报错但功能静默失效」缺陷，
 * 必须靠用例锁死。写入端与查询端都依赖本工具，覆盖价值高。
 */
@Tag("unit")
@DisplayName("L1 · 城市名归一化 CityUtil")
class CityUtilTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // 直辖市：省市两段相同，取哪段都对
            "北京市/北京市/朝阳区,     北京",
            "上海市/上海市/徐汇区,     上海",
            // 普通省市区
            "广东省/深圳市/南山区,     深圳",
            "河南省/驻马店市/驿城区,   驻马店",
            // 自治区：长后缀必须先于「区」被剥离，否则会截成「内蒙古自治」
            "内蒙古自治区/呼和浩特市/新城区,       呼和浩特",
            "新疆维吾尔自治区/乌鲁木齐市/天山区,   乌鲁木齐",
            // 自治州
            "新疆维吾尔自治区/伊犁哈萨克自治州/伊宁市, 伊犁哈萨克",
            // 裸城市名（历史种子数据）
            "驻马店市,               驻马店",
            "北京,                   北京",
            // 空格分隔
            "广东省 深圳市 南山区,    深圳"
    })
    void 各种输入格式都收敛为不带行政后缀的短名(String raw, String expected) {
        assertThat(CityUtil.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void 空值与空白输入统一返回_null_便于直接写库存NULL(String raw) {
        assertThat(CityUtil.normalize(raw)).isNull();
    }

    /**
     * 🐞 已知缺陷（本次测试新发现）：纯分隔符输入触发 ArrayIndexOutOfBoundsException。
     *
     * <p>复现：{@code CityUtil.normalize("//")}
     * <pre>
     *   "//".trim()               → "//"        非空，通过 isEmpty 守卫
     *   "//".split("[/\\s]+")     → 长度 0 的数组（Java 会丢弃全部尾部空串）
     *   parts.length >= 2 ? ... : parts[0]  → parts[0] 越界抛异常
     * </pre>
     *
     * <p>影响：{@code CityUtil.normalize()} 在发帖与改资料两个<b>写入端</b>被调用，
     * 入参直接来自前端 body.city。用户提交 {@code "//"}、{@code "/"} 之类的脏数据
     * 会让接口抛 500 而非按契约返回 null。属于未受控的输入边界。
     *
     * <p>建议修复：{@code parts.length == 0} 时返回 null（与其他空值路径一致）。
     *
     * <p>本用例断言的是<b>期望行为</b>而非当前行为 —— 修复后应能直接通过并移除
     * {@code @Tag("known-issue")}。
     */
    @Tag("known-issue")
    @ParameterizedTest
    @ValueSource(strings = {"//", "/", "  /  /  "})
    @DisplayName("🐞 纯分隔符输入应返回 null（当前抛 ArrayIndexOutOfBoundsException）")
    void 纯分隔符输入应返回null_当前抛数组越界(String raw) {
        assertThat(CityUtil.normalize(raw)).isNull();
    }

    @Test
    void 写入端与查询端对同一城市的不同表述必须归一到同一结果() {
        // 这正是本工具存在的意义：三个来源的格式差异不能导致同城互相看不见
        String fromProfile  = CityUtil.normalize("北京市/北京市/朝阳区");  // 资料级联器
        String fromPostBody = CityUtil.normalize("北京市");                // 发帖接口
        String fromSeed     = CityUtil.normalize("北京");                  // 历史种子

        assertThat(fromProfile).isEqualTo(fromPostBody).isEqualTo(fromSeed);
    }

    @Test
    void 后缀等于整个城市名时不被剥离为空() {
        // 边界：city.length() > suffix.length() 的守卫，避免「市」被剥成空串
        assertThat(CityUtil.normalize("市")).isEqualTo("市");
    }

    @Test
    void 只剥离一次后缀_不递归剥离() {
        // 「县」剥离后剩「双柏」，不应继续处理
        assertThat(CityUtil.normalize("云南省/楚雄彝族自治州/双柏县")).isEqualTo("楚雄彝族");
    }
}
