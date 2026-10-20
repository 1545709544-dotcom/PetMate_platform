package com.petmate.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试数据工厂 —— 用 SQL 直接造种子数据。
 *
 * <h3>为什么直接写 SQL 而不是调接口</h3>
 * 用接口造数据会让「前置条件」依赖「被测对象」：下单测试若靠注册接口造用户，
 * 注册接口一坏，下单测试也跟着红，故障定位被污染。直接 INSERT 让前置条件稳定且快。
 *
 * <h3>ID 分配</h3>
 * 用 {@link AtomicLong} 从高位起分配（10_000_000+），避开种子数据与自增主键区间，
 * 保证并发用例之间不会撞 ID。
 *
 * <p><b>注意：以下 INSERT 的表名与列名需与 {@code tests/backend/resources/db/schema_full.sql}
 * 对齐。骨架阶段先给出主干表，随用例补充。</b>
 */
@Component
public class TestDataFactory {

    @Autowired
    private JdbcTemplate jdbc;

    /** 测试数据 ID 起始值，远离业务数据区间。 */
    private static final long ID_BASE = 10_000_000L;
    private final AtomicLong seq = new AtomicLong(ID_BASE);

    public long nextId() {
        return seq.incrementAndGet();
    }

    // ==================== 用户 ====================

    /**
     * 创建普通用户。
     *
     * @param status 1=正常，0=封禁（用于验证封禁后 token 是否被拒，关联 B-A4）
     * @return 新建用户 id
     */
    public long createUser(String phone, int status) {
        long id = nextId();
        jdbc.update("""
                INSERT INTO t_user (id, phone, nickname, role, status, created_at)
                VALUES (?, ?, ?, 'user', ?, NOW())
                """, id, phone, "测试用户" + id, status);
        return id;
    }

    public long createUser(String phone) {
        return createUser(phone, 1);
    }

    /** 创建管理员。 */
    public long createAdmin(String phone) {
        long id = nextId();
        jdbc.update("""
                INSERT INTO t_user (id, phone, nickname, role, status, created_at)
                VALUES (?, ?, ?, 'admin', 1, NOW())
                """, id, phone, "测试管理员" + id);
        return id;
    }

    // ==================== 商家与商品 ====================

    /** 创建店铺，返回 merchantId。 */
    public long createMerchant(long ownerUserId, String name) {
        long id = nextId();
        jdbc.update("""
                INSERT INTO t_merchant (id, user_id, name, status, created_at)
                VALUES (?, ?, ?, 1, NOW())
                """, id, ownerUserId, name);
        return id;
    }

    /**
     * 创建商品及其单一 SKU。
     *
     * @param stock 初始库存 —— 并发超卖用例的核心输入
     * @return SKU id（下单接口用的是 skuId）
     */
    public long createProductWithSku(long merchantId, String title, BigDecimal price, int stock) {
        long productId = nextId();
        jdbc.update("""
                INSERT INTO t_product (id, merchant_id, title, price, status, sale_count, created_at)
                VALUES (?, ?, ?, ?, 1, 0, NOW())
                """, productId, merchantId, title, price);

        long skuId = nextId();
        jdbc.update("""
                INSERT INTO t_sku (id, product_id, spec_name, price, stock)
                VALUES (?, ?, '默认规格', ?, ?)
                """, skuId, productId, price, stock);
        return skuId;
    }

    /** 读取 SKU 当前库存 —— 并发用例的最终断言依据。 */
    public Integer stockOf(long skuId) {
        return jdbc.queryForObject("SELECT stock FROM t_sku WHERE id = ?", Integer.class, skuId);
    }

    // ==================== 积分 ====================

    /** 设定用户积分余额（关联并发丢更新 B-M1）。 */
    public void setPoints(long userId, int balance) {
        jdbc.update("""
                INSERT INTO t_points_account (user_id, balance, created_at)
                VALUES (?, ?, NOW())
                ON DUPLICATE KEY UPDATE balance = VALUES(balance)
                """, userId, balance);
    }

    public Integer pointsOf(long userId) {
        return jdbc.queryForObject(
                "SELECT balance FROM t_points_account WHERE user_id = ?", Integer.class, userId);
    }

    // ==================== 清理 ====================

    /**
     * 清理本工厂造出的全部数据（id >= ID_BASE）。
     *
     * <p>并发用例无法依赖 Spring 的事务回滚（必须真实提交才能验证竞态），
     * 因此需要在 {@code @AfterEach} 显式调用本方法。
     *
     * <p>按外键依赖倒序删除。
     */
    public void cleanUp() {
        String[] tables = {
                "t_order_item", "t_order", "t_points_log", "t_points_account",
                "t_sku", "t_product", "t_merchant", "t_user"
        };
        for (String t : tables) {
            try {
                jdbc.update("DELETE FROM " + t + " WHERE id >= ?", ID_BASE);
            } catch (Exception ignored) {
                // 部分表主键非 id（如 t_points_account 以 user_id 为主键），
                // 或该表在当前 schema 中不存在 —— 忽略，避免清理逻辑掩盖真实失败。
            }
        }
        try {
            jdbc.update("DELETE FROM t_points_account WHERE user_id >= ?", ID_BASE);
        } catch (Exception ignored) {
            // 同上
        }
    }
}
