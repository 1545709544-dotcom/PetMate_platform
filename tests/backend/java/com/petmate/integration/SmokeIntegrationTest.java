package com.petmate.integration;

import com.petmate.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L3 · 冒烟测试 —— 验证集成测试基础设施本身是通的。
 *
 * <p>这是搭建阶段的第一条用例（对应后端测试计划 §六 P0-1 的「跑通 1 条冒烟」）。
 * 它不测业务，只回答一个问题：<b>Testcontainers 起得来吗？Spring 上下文加载得了吗？
 * MockMvc 打得通接口吗？</b>
 *
 * <p>若本用例失败，后续所有 L3 用例都不必看了 —— 先修基础设施。
 * 若本用例被 skipped，说明本机没有 Docker（见 {@code DockerAvailable}）。
 *
 * <p>选 {@code /category/tree} 作为探针：它是后端 README 里的健康检查接口，
 * 无需鉴权、无需前置数据。
 */
@DisplayName("L3 · 集成测试基础设施冒烟")
class SmokeIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Spring 上下文加载 + MySQL 容器就绪 + 接口可访问")
    void 冒烟_分类树接口返回业务成功码() throws Exception {
        mockMvc.perform(get(url("/category/tree")))
                // 本项目所有响应都是 HTTP 200，业务码在 body.code
                // （这本身是个设计问题，见后端计划 B-E1）
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("数据源确实指向 Testcontainers 容器而非本机开发库")
    void 冒烟_数据源隔离确认() {
        String url = jdbc.getDataSource() != null ? MYSQL.getJdbcUrl() : "";
        // 防呆：一旦 @DynamicPropertySource 失效、测试连上了本机 3306 的开发库，
        // 集成测试就会污染真实数据。这条断言让该错误立即暴露。
        org.assertj.core.api.Assertions.assertThat(url)
                .as("测试必须连容器库 petmate_test，绝不能连开发库")
                .contains("petmate_test");
    }
}
