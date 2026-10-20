package com.petmate.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发压测工具 —— L3-b 并发用例的执行器。
 *
 * <h3>为什么需要「统一发令」</h3>
 * 直接 {@code for + executor.submit()} 提交 N 个任务，线程实际是<b>陆续</b>启动的：
 * 第 1 个任务可能在第 50 个提交前就跑完了，根本形成不了竞态，
 * 于是「超卖测试」永远是绿的 —— 测了个寂寞。
 *
 * <p>本工具用两道 {@link CountDownLatch} 解决：
 * <ol>
 *   <li>{@code ready} —— 主线程等所有工作线程就位</li>
 *   <li>{@code start} —— 一声令下，所有线程同时冲</li>
 * </ol>
 * 这样才能真正把请求压在同一瞬间，暴露 CAS 缺失、丢更新、TOCTOU 这类问题。
 */
public final class ConcurrencyHelper {

    private ConcurrencyHelper() {}

    /** 并发执行结果。 */
    public record Result<T>(
            int successCount,
            int failureCount,
            List<T> successResults,
            List<Throwable> failures
    ) {
        /** 断言辅助：成功数是否恰好等于期望值。 */
        public boolean successIs(int expected) {
            return successCount == expected;
        }
    }

    /**
     * 让 {@code threads} 个线程同时执行 {@code task}，等全部结束后返回统计结果。
     *
     * <p>任务抛异常记为失败并收集异常（业务上「库存不足」正是以异常形式出现，
     * 属于预期结果，不应中断整个用例）。
     *
     * @param threads 并发线程数
     * @param task    每个线程执行的任务；入参为线程序号（0-based），便于区分用户/订单
     */
    public static <T> Result<T> runConcurrently(int threads, ThrowingFunction<Integer, T> task)
            throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<T> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();               // ← 所有线程在此处齐步等待
                    T r = task.apply(idx);
                    results.add(r);
                    success.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(30, TimeUnit.SECONDS);       // 等所有线程就位
        start.countDown();                       // 发令
        done.await(120, TimeUnit.SECONDS);       // 等全部完成
        pool.shutdownNow();

        return new Result<>(success.get(), failure.get(), results, errors);
    }

    /** 允许抛出受检异常的 Function（JDK 内置的 Function 不允许）。 */
    @FunctionalInterface
    public interface ThrowingFunction<I, O> {
        O apply(I input) throws Exception;
    }

    /**
     * 并发执行无返回值的任务。
     *
     * <p>刻意<b>不</b>做成 {@code runConcurrently} 的重载：lambda 表达式对
     * {@code ThrowingFunction} 与 {@code ThrowingRunnable} 两个函数式接口
     * 都适用，编译器无法推断，会直接报「引用不明确」。
     */
    public static Result<Void> runConcurrentlyVoid(int threads, ThrowingRunnable task)
            throws InterruptedException {
        return runConcurrently(threads, idx -> {
            task.run(idx);
            return null;
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run(int index) throws Exception;
    }
}
