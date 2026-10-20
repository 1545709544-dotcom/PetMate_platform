package com.petmate.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Docker 可用性探测 —— 决定 L3 集成测试是「执行」还是「跳过」。
 *
 * <p>背景：本项目的 L3 集成测试依赖 Testcontainers 启动真实 MySQL 8.0，
 * 而 Testcontainers 需要本机有可用的 Docker daemon。若 Docker 未运行，
 * 容器启动会抛出 {@code IllegalStateException}，表现为一大片「测试失败」——
 * 但这是<b>环境缺失</b>，不是<b>代码缺陷</b>，两者必须在报告里区分开。
 *
 * <p>因此集成测试基类统一挂 {@link RequiresDocker}：Docker 不可用时用例被标记为
 * <b>skipped</b> 而非 failed，{@code mvnw.cmd test} 依然绿灯通过，
 * L1 单元测试不受任何影响。
 *
 * <p>探测结果做了进程级缓存 —— Docker 环境在单次构建内不会变化，
 * 每个类都重新探测一次会显著拖慢启动。
 */
public final class DockerAvailable implements ExecutionCondition {

    private static volatile Boolean cached;

    /** 探测本机 Docker 是否可用。结果在 JVM 内缓存。 */
    public static boolean isAvailable() {
        Boolean c = cached;
        if (c != null) return c;
        synchronized (DockerAvailable.class) {
            if (cached == null) {
                cached = probe();
            }
            return cached;
        }
    }

    private static boolean probe() {
        try {
            // 反射调用，避免 Docker 缺失时因类加载失败而抛出难以理解的异常
            Class<?> clientFactory = Class.forName(
                    "org.testcontainers.DockerClientFactory");
            Object instance = clientFactory.getMethod("instance").invoke(null);
            Object available = clientFactory.getMethod("isDockerAvailable").invoke(instance);
            return Boolean.TRUE.equals(available);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        if (isAvailable()) {
            return ConditionEvaluationResult.enabled("Docker 可用，执行集成测试");
        }
        return ConditionEvaluationResult.disabled(
                "跳过：本机 Docker 不可用，Testcontainers 无法启动 MySQL 容器。"
                        + "启动 Docker Desktop 后重跑即可执行本组集成测试。");
    }
}
