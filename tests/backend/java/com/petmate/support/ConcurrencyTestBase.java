package com.petmate.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * L3-b 并发测试基类。
 *
 * <h3>与普通集成测试的关键差异：不能用事务回滚</h3>
 * Spring 测试默认把用例包在一个事务里，结束时回滚。但并发用例中，
 * <b>工作线程各自持有独立的数据库连接</b>，看不见主线程事务里未提交的数据 ——
 * 造好的商品/用户对工作线程根本不存在，用例会以「数据不存在」的形式莫名失败。
 *
 * <p>因此这里显式关掉事务托管（{@link Propagation#NOT_SUPPORTED}），
 * 让所有写入真实提交，改由 {@link #cleanUpAfterEach()} 在每个用例后手工清理。
 */
@Rollback(false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("concurrency")
public abstract class ConcurrencyTestBase extends IntegrationTestBase {

    /**
     * 每个并发用例结束后清理其造出的数据。
     *
     * <p>不清理会导致：① 用例之间互相污染（上一个用例的库存残留）
     * ② 唯一键冲突（手机号/订单号重复）。
     */
    @AfterEach
    void cleanUpAfterEach() {
        data.cleanUp();
    }
}
