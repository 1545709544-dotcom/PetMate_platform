-- ============================================================
-- v3.0: 订单 30 分钟未支付自动取消
--   - 新增 t_order.expire_at (DATETIME, NULL)
--   - backfill: status='pending_pay' 的订单统一设为 created_at + 30 分钟
--   - 已支付/已取消订单保持 NULL (没有超时意义)
-- ============================================================

-- 幂等：先看列存不存在
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_order'
    AND column_name = 'expire_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN expire_at DATETIME NULL COMMENT ''待支付订单的过期时间(NOW+30min)'' AFTER paid_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引：scheduler 扫描 status+expire_at 命中率高,加复合索引
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_order'
    AND index_name = 'idx_status_expire'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE t_order ADD KEY idx_status_expire (status, expire_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- backfill: pending_pay 且 expire_at 为空 → created_at + 30 min
UPDATE t_order
   SET expire_at = DATE_ADD(created_at, INTERVAL 30 MINUTE)
 WHERE status = 'pending_pay'
   AND expire_at IS NULL;