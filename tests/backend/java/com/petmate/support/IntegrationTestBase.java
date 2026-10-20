package com.petmate.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.PetMateApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L3 集成测试基类 —— 真实 Spring 上下文 + 真实 MySQL + MockMvc。
 *
 * <h3>为什么后端以集成测试为主</h3>
 * 本项目 29/32 的 Controller 直接注入 {@code JdbcTemplate}，业务逻辑与 SQL 写在一起，
 * 没有 Service 层。Mock 掉 {@code JdbcTemplate} 等于 mock 掉被测对象 —— 测的是 stub 而非业务。
 * 真正要验证的规则（库存 CAS 是否生效、事务是否回滚、状态跃迁是否被 DB 约束挡住）
 * 只有真实数据库能给出答案。
 *
 * <h3>容器生命周期</h3>
 * {@code static} 容器 + Testcontainers 的 reuse 机制 → 整个测试套件<b>共用一个</b> MySQL 实例，
 * 而非每个测试类重启一次（后者会让套件慢到不可用）。
 *
 * <h3>数据隔离</h3>
 * 默认<b>不</b>加 {@code @Transactional} 自动回滚，因为并发用例必须真实提交才能验证竞态。
 * 各子类按需选择：
 * <ul>
 *   <li>普通集成用例 —— 自行标注 {@code @Transactional}，由 Spring 测试框架回滚</li>
 *   <li>并发用例 —— 继承 {@link ConcurrencyTestBase}，在 {@code @AfterEach} 手工清理</li>
 * </ul>
 *
 * <h3>Docker 缺失时的行为</h3>
 * 挂了 {@link RequiresDocker}：本机无 Docker 时整组用例标记为 skipped，
 * 不会让 {@code mvnw.cmd test} 变红。
 */
@SpringBootTest(
        classes = PetMateApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@RequiresDocker
@Tag("integration")
public abstract class IntegrationTestBase {

    /**
     * 全套件共享的 MySQL 8.0 容器。
     *
     * <p>{@code withReuse(true)} 需要本机 {@code ~/.testcontainers.properties} 中设置
     * {@code testcontainers.reuse.enable=true} 才生效；未设置时行为退化为
     * 「套件结束即销毁」，功能正确性不受影响，只是慢一些。
     *
     * <p>建库脚本见 {@code tests/backend/resources/db/schema_full.sql}。
     */
    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("petmate_test")
                    .withUsername("petmate")
                    .withPassword("petmate")
                    .withUrlParam("useUnicode", "true")
                    .withUrlParam("characterEncoding", "utf8")
                    .withUrlParam("serverTimezone", "Asia/Shanghai")
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withUrlParam("useSSL", "false")
                    // 容器首次启动时执行建表脚本
                    .withInitScript("db/schema_full.sql")
                    .withReuse(true);

    static {
        // 仅在 Docker 可用时启动，否则让 @RequiresDocker 把用例判为 skipped。
        // 直接 static 块启动（而非 @Container）是为了配合 reuse：
        // @Container 会在每个类结束后 stop，reuse 就失去意义。
        if (DockerAvailable.isAvailable()) {
            MYSQL.start();
        }
    }

    /** 把容器的实际 JDBC 地址注入 Spring 环境，覆盖 application-test.yml 中的占位值。 */
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        if (!DockerAvailable.isAvailable()) return;
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TokenFactory tokens;
    @Autowired protected TestDataFactory data;

    /** 所有接口都带 context-path 前缀，避免每个用例手写。 */
    protected static final String API = "/api/v1";

    protected String url(String path) {
        return path.startsWith("/") ? API + path : API + "/" + path;
    }
}
