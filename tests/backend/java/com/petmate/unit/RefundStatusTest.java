package com.petmate.unit;

import com.petmate.refund.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 · 售后状态机常量与流转规则。
 *
 * <p>被测：{@code com.petmate.refund.RefundStatus}
 *
 * <p>售后是全项目最复杂的逻辑（11 个状态、3 条主线、跨用户/商家/平台三端驱动，
 * 关联后端计划 B-S1）。状态机本身是纯静态方法，无 DB 依赖 —— 这是后端少数
 * 「Mock 单测性价比极高」的地方，值得先把规则钉死，防止后续改动悄悄破坏跃迁。
 */
@Tag("unit")
@DisplayName("L1 · 售后状态机 RefundStatus")
class RefundStatusTest {

    @Nested
    @DisplayName("售后类型校验")
    class TypeValidation {

        @ParameterizedTest(name = "合法类型：{0}")
        @ValueSource(strings = {"REFUND_ONLY", "RETURN_REFUND", "EXCHANGE"})
        void 三种合法售后类型都被接受(String type) {
            assertThat(RefundStatus.isValidType(type)).isTrue();
        }

        @ParameterizedTest(name = "非法类型：{0}")
        @ValueSource(strings = {"refund_only", "REFUND", "UNKNOWN", ""})
        void 非法类型被拒绝(String type) {
            assertThat(RefundStatus.isValidType(type)).isFalse();
        }

        /**
         * 🐞 已知缺陷（本次测试新发现）：{@code isValidType(null)} 抛 NullPointerException。
         *
         * <p>根因：{@code TYPE_TEXT} 是 {@code Map.of(...)} 创建的不可变 Map，
         * 其 {@code containsKey(null)} 会 {@code Objects.requireNonNull} 抛 NPE
         * （而非像 HashMap 那样返回 false）。
         *
         * <p>影响：售后申请接口若未对 body 里的 {@code refundType} 做非空校验，
         * 用户提交 {@code {"refundType": null}} 会得到 500 而非「参数非法」的业务错误。
         *
         * <p>建议修复：{@code return type != null && TYPE_TEXT.containsKey(type);}
         */
        @Test
        @Tag("known-issue")
        @DisplayName("🐞 null 类型应被拒绝（当前抛 NullPointerException）")
        void null_类型应被拒绝_当前抛NPE() {
            assertThat(RefundStatus.isValidType(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("终态判定")
    class FinalStatus {

        @ParameterizedTest(name = "终态：{0}")
        @ValueSource(strings = {"refunded", "exchanged", "rejected", "arbitrated", "closed"})
        void 五个终态被正确识别(String status) {
            assertThat(RefundStatus.isFinal(status)).isTrue();
        }

        @ParameterizedTest(name = "非终态：{0}")
        @ValueSource(strings = {"pending", "returning", "exchanging", "returned", "reshipped", "appealing"})
        void 进行中状态不是终态(String status) {
            assertThat(RefundStatus.isFinal(status)).isFalse();
        }

        @Test
        void 终态集合恰好包含五个状态_防止误增删() {
            // 固化集合大小：新增终态必须同步 review 所有依赖终态判断的分支
            assertThat(RefundStatus.FINAL_STATUS)
                    .hasSize(5)
                    .containsExactlyInAnyOrder(
                            RefundStatus.REFUNDED, RefundStatus.EXCHANGED,
                            RefundStatus.REJECTED, RefundStatus.ARBITRATED,
                            RefundStatus.CLOSED);
        }

        /**
         * 🐞 已知缺陷（本次测试新发现）：{@code isFinal(null)} 抛 NullPointerException。
         *
         * <p>根因：{@code FINAL_STATUS} 是 {@code List.of(...)} 不可变列表，
         * 其 {@code contains(null)} 抛 NPE 而非返回 false。
         *
         * <p>影响：{@code isFinal()} 被用于「终态不可再流转」的守卫。
         * 若某条售后单的 status 因历史数据或迁移遗漏为 NULL，
         * 商家点击处理时会 500 —— 而正确行为应是「非终态，允许处理」或明确报错。
         *
         * <p>建议修复：{@code return status != null && FINAL_STATUS.contains(status);}
         */
        @Test
        @Tag("known-issue")
        @DisplayName("🐞 null 状态不应被判为终态（当前抛 NullPointerException）")
        void null_状态不应被判为终态_当前抛NPE() {
            assertThat(RefundStatus.isFinal(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("商家同意后的首次跃迁")
    class AfterApprove {

        @ParameterizedTest(name = "{0} 同意后 → {1}")
        @CsvSource({
                "REFUND_ONLY,   refunded",    // 仅退款：直接完成
                "RETURN_REFUND, returning",   // 退货退款：待用户寄回
                "EXCHANGE,      exchanging"   // 换货：待用户寄回
        })
        void 三种类型各自进入正确的下一状态(String type, String expected) {
            assertThat(RefundStatus.afterApprove(type)).isEqualTo(expected);
        }

        @Test
        void 未知类型兜底为直接退款() {
            // 兜底行为：宁可退款也不要卡住流程。此断言固化当前设计意图，
            // 若未来改为抛异常，本用例会失败并提醒同步 review 调用方。
            assertThat(RefundStatus.afterApprove("UNKNOWN")).isEqualTo(RefundStatus.REFUNDED);
            assertThat(RefundStatus.afterApprove(null)).isEqualTo(RefundStatus.REFUNDED);
        }
    }

    @Nested
    @DisplayName("是否需要用户寄回")
    class NeedReturn {

        @Test
        void 退货退款与换货需要寄回_仅退款不需要() {
            assertThat(RefundStatus.needReturn(RefundStatus.TYPE_RETURN_REFUND)).isTrue();
            assertThat(RefundStatus.needReturn(RefundStatus.TYPE_EXCHANGE)).isTrue();
            assertThat(RefundStatus.needReturn(RefundStatus.TYPE_REFUND_ONLY)).isFalse();
            assertThat(RefundStatus.needReturn(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("各操作的允许状态集合")
    class AllowedStatusSets {

        @Test
        void 可撤销状态_仅限商家尚未实质处理完的三态() {
            assertThat(RefundStatus.CAN_CANCEL)
                    .containsExactlyInAnyOrder(
                            RefundStatus.PENDING, RefundStatus.RETURNING, RefundStatus.EXCHANGING);
            // 终态一律不可撤销 —— 这条是资金安全的关键防线
            for (String finalStatus : RefundStatus.FINAL_STATUS) {
                assertThat(RefundStatus.CAN_CANCEL).doesNotContain(finalStatus);
            }
        }

        @Test
        void 可回填物流状态_仅限待寄回的两态() {
            assertThat(RefundStatus.CAN_FILL_LOGISTICS)
                    .containsExactlyInAnyOrder(RefundStatus.RETURNING, RefundStatus.EXCHANGING);
        }

        @Test
        void 可申请平台介入状态_包含被驳回与停滞在商家侧的状态() {
            assertThat(RefundStatus.CAN_APPEAL)
                    .containsExactlyInAnyOrder(
                            RefundStatus.REJECTED, RefundStatus.PENDING,
                            RefundStatus.RETURNED, RefundStatus.RESHIPPED);
        }

        @Test
        void 商家待办_仅限需要商家动作的两态() {
            assertThat(RefundStatus.MERCHANT_TODO)
                    .containsExactlyInAnyOrder(RefundStatus.PENDING, RefundStatus.RETURNED);
        }

        @Test
        void 允许发起售后的订单状态_仅已支付之后() {
            // 未支付订单不应能发起售后（否则是凭空退款）
            assertThat(RefundStatus.ORDER_CAN_APPLY)
                    .containsExactlyInAnyOrder("paid", "shipping", "done")
                    .doesNotContain("pending_pay", "cancelled");
        }
    }

    @Nested
    @DisplayName("文案映射")
    class TextMapping {

        @Test
        void 全部十一个状态都有中文文案_无遗漏() {
            String[] all = {
                    RefundStatus.PENDING, RefundStatus.RETURNING, RefundStatus.EXCHANGING,
                    RefundStatus.RETURNED, RefundStatus.RESHIPPED, RefundStatus.REFUNDED,
                    RefundStatus.EXCHANGED, RefundStatus.REJECTED, RefundStatus.APPEALING,
                    RefundStatus.ARBITRATED, RefundStatus.CLOSED
            };
            for (String s : all) {
                // 文案不应等于状态码本身（等于说明落到了兜底分支 = 漏配）
                assertThat(RefundStatus.statusText(s))
                        .as("状态 %s 缺少中文文案", s)
                        .isNotEqualTo(s)
                        .isNotBlank();
            }
        }

        @Test
        void 未知状态兜底返回原值() {
            assertThat(RefundStatus.statusText("weird")).isEqualTo("weird");
        }

        /**
         * 🐞 已知缺陷（本次测试新发现）：{@code statusText(null)} 抛 NullPointerException。
         *
         * <p>根因：源码写的是
         * {@code STATUS_TEXT.getOrDefault(status, status == null ? "" : status)} ——
         * 三元表达式确实考虑了 null，但 {@code Map.ofEntries()} 不可变 Map 的
         * {@code getOrDefault} <b>先</b>对 key 做 requireNonNull，
         * 根本走不到 defaultValue。这是一个「看起来处理了 null、实际没有」的陷阱。
         *
         * <p>影响：售后列表渲染时若有 status 为 NULL 的历史单，整个列表接口 500。
         *
         * <p>建议修复：{@code if (status == null) return ""; return STATUS_TEXT.getOrDefault(status, status);}
         */
        @Test
        @Tag("known-issue")
        @DisplayName("🐞 null 状态文案应返回空串（当前抛 NullPointerException）")
        void null_状态文案应返回空串_当前抛NPE() {
            assertThat(RefundStatus.statusText(null)).isEmpty();
        }

        @Test
        void 类型文案正确_未知类型兜底为售后() {
            assertThat(RefundStatus.typeText(RefundStatus.TYPE_REFUND_ONLY)).isEqualTo("仅退款");
            assertThat(RefundStatus.typeText(RefundStatus.TYPE_RETURN_REFUND)).isEqualTo("退货退款");
            assertThat(RefundStatus.typeText(RefundStatus.TYPE_EXCHANGE)).isEqualTo("换货");
            assertThat(RefundStatus.typeText("UNKNOWN")).isEqualTo("售后");
        }
    }
}
