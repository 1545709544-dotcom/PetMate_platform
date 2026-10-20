package com.petmate.concurrency;

import com.petmate.support.ConcurrencyHelper;
import com.petmate.support.ConcurrencyTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L3-b · 库存并发正确性。
 *
 * <p>关联后端测试计划 B-M8：{@code OrderController.java:322} 的库存扣减
 * 已使用 SQL CAS（{@code UPDATE ... WHERE stock >= ?}），单 SKU 场景是安全的。
 *
 * <p><b>那为什么还要写这条用例？</b>
 * 因为「当前是对的」和「以后一直是对的」是两回事。CAS 这类保证极易在重构中被
 * 无意破坏 —— 比如有人为了「可读性」把 SQL 拆成先 SELECT 再 UPDATE，
 * 功能测试全绿，超卖却已经埋下。本用例把这条保证<b>固化</b>为回归防线。
 *
 * <p>本类为骨架示例，展示并发用例的标准写法：
 * <ul>
 *   <li>继承 {@link ConcurrencyTestBase} —— 关闭事务托管（工作线程看不见未提交数据）</li>
 *   <li>用 {@link ConcurrencyHelper} 统一发令 —— 否则线程陆续启动，压根形成不了竞态</li>
 *   <li>断言「成功数」而非「无异常」—— 库存不足时下单失败是<b>正确行为</b></li>
 * </ul>
 */
@DisplayName("L3-b · 库存并发（超卖防护）")
class StockConcurrencyTest extends ConcurrencyTestBase {

    @Test
    @Disabled("待 P0-0 补全 schema_full.sql 且确认下单接口契约后启用")
    @DisplayName("库存 10 · 50 线程各下单 1 件 → 恰好成功 10 单，库存归零不为负")
    void 单SKU并发下单不超卖() throws Exception {
        // ---------- given：库存 10 的商品 ----------
        long ownerId = data.createUser("13900000001");
        long merchantId = data.createMerchant(ownerId, "并发测试店");
        long skuId = data.createProductWithSku(
                merchantId, "并发测试商品", new BigDecimal("9.90"), 10);

        // 50 个不同用户，避免限购规则干扰
        long[] buyers = new long[50];
        for (int i = 0; i < 50; i++) {
            buyers[i] = data.createUser("1390000" + String.format("%04d", i + 100));
        }

        // ---------- when：50 线程同时下单 ----------
        var result = ConcurrencyHelper.runConcurrently(50, idx -> {
            String token = tokens.user(buyers[idx]);
            // TODO(P0-1): 替换为真实下单请求，例如
            // mockMvc.perform(post(url("/order"))
            //         .header("Authorization", TokenFactory.bearer(token))
            //         .contentType(MediaType.APPLICATION_JSON)
            //         .content(objectMapper.writeValueAsString(Map.of(
            //                 "skuId", skuId, "quantity", 1, "addressId", addressId))))
            //     .andReturn();
            // 并按 body.code == 0 判定成功、库存不足码判定失败。
            throw new UnsupportedOperationException("待接入真实下单接口");
        });

        // ---------- then：成功数 = 库存数，且库存不为负 ----------
        assertThat(result.successCount())
                .as("库存 10 件，50 人抢购，必须恰好 10 单成功")
                .isEqualTo(10);

        assertThat(data.stockOf(skuId))
                .as("库存必须归零且绝不为负 —— 为负即超卖")
                .isZero();
    }
}
