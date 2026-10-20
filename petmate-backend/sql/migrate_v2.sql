-- ============================================================
-- PetMate v2.0 增量迁移脚本
-- 用于把 6 处演示态硬编码升级到生产级所需的所有表/字段
-- 此脚本可重跑（所有 ALTER/CREATE 均带存在性判断）
-- 建议在 schema.sql 之外独立执行：mysql -uroot -p123456 petmate < migrate_v2.sql
-- ============================================================

-- ============= t_order 扩展支付字段 =============
-- paid_at 已在 schema.sql 存在；这里只补 pay_channel/out_trade_no/trade_no/refund_*

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND COLUMN_NAME = 'pay_channel');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN pay_channel VARCHAR(16) DEFAULT NULL COMMENT ''alipay/wechat/balance'' AFTER status',
  'SELECT ''t_order.pay_channel already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND COLUMN_NAME = 'out_trade_no');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN out_trade_no VARCHAR(64) DEFAULT NULL COMMENT ''商户订单号'' AFTER pay_channel',
  'SELECT ''t_order.out_trade_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND COLUMN_NAME = 'trade_no');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN trade_no VARCHAR(64) DEFAULT NULL COMMENT ''支付宝交易号'' AFTER out_trade_no',
  'SELECT ''t_order.trade_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND COLUMN_NAME = 'refund_status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN refund_status VARCHAR(16) DEFAULT NULL COMMENT ''none/partial/full'' AFTER paid_at',
  'SELECT ''t_order.refund_status already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND COLUMN_NAME = 'refund_amount');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE t_order ADD COLUMN refund_amount DECIMAL(10,2) DEFAULT 0 AFTER refund_status',
  'SELECT ''t_order.refund_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_order'
                      AND INDEX_NAME = 'idx_out_trade_no');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE t_order ADD KEY idx_out_trade_no (out_trade_no)',
  'SELECT ''t_order.idx_out_trade_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============= 支付日志 t_payment_log =============
CREATE TABLE IF NOT EXISTS t_payment_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32)  NOT NULL,
  out_trade_no  VARCHAR(64)  NOT NULL,
  trade_no      VARCHAR(64)  DEFAULT NULL,
  channel       VARCHAR(16)  NOT NULL,
  action        VARCHAR(16)  NOT NULL COMMENT 'precreate/notify/refund',
  amount        DECIMAL(10,2) NOT NULL,
  status        VARCHAR(16)  NOT NULL COMMENT 'pending/success/failed',
  raw_payload   JSON         DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  KEY idx_order_no (order_no),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付/对账日志';

-- ============= 埋点事件 t_event =============
CREATE TABLE IF NOT EXISTS t_event (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       DEFAULT 0,
  role        VARCHAR(16)  DEFAULT 'user',
  event       VARCHAR(32)  NOT NULL COMMENT 'pv/click/addCart/submitOrder/paySuccess/...',
  page        VARCHAR(64)  DEFAULT NULL,
  target_type VARCHAR(16)  DEFAULT NULL COMMENT 'product/post/order',
  target_id   BIGINT       DEFAULT NULL,
  merchant_id BIGINT       DEFAULT 0,
  payload     JSON         DEFAULT NULL,
  trace_id    VARCHAR(32)  DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_event_created (event, created_at),
  KEY idx_merchant_created (merchant_id, created_at),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='埋点事件';

-- ============= 帖子收藏 t_post_collect =============
CREATE TABLE IF NOT EXISTS t_post_collect (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  post_id     BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏';

-- ============= 优惠券去重 UNIQUE 索引 =============
-- 步骤 1：清理历史重复行（保留每组 id 最小的一条）
DELETE uc1 FROM t_user_coupon uc1
INNER JOIN t_user_coupon uc2
  ON uc1.user_id = uc2.user_id
 AND uc1.coupon_id = uc2.coupon_id
 AND uc1.id > uc2.id;

-- 步骤 2：加 UNIQUE 索引（不存在才加）
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 't_user_coupon'
                      AND INDEX_NAME = 'uk_user_coupon');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE t_user_coupon ADD UNIQUE KEY uk_user_coupon (user_id, coupon_id)',
  'SELECT ''t_user_coupon.uk_user_coupon already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 验证
-- ============================================================
SELECT 't_payment_log' AS tbl, COUNT(*) AS cnt FROM t_payment_log
UNION ALL SELECT 't_event', COUNT(*) FROM t_event
UNION ALL SELECT 't_post_collect', COUNT(*) FROM t_post_collect
UNION ALL SELECT 't_order.has_out_trade_no', COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_order' AND COLUMN_NAME='out_trade_no'
UNION ALL SELECT 't_user_coupon.has_uk', COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_user_coupon' AND INDEX_NAME='uk_user_coupon';