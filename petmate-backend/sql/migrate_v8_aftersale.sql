-- ============================================================
-- v8.0: 售后（After-sales）全量升级
--   目标：把只有「仅退款 / 退货退款」半成品的 t_refund，
--         升级为支持 仅退款 / 退货退款 / 换货 + 平台介入仲裁 + 商家改金额 的完整售后单
--
--   状态机（status，共 11 态）:
--     pending     待商家处理（三种类型的共同入口）
--     returning   商家已同意退货，待用户寄回（退货退款线）
--     exchanging  商家已同意换货，待用户寄回（换货线）
--     returned    用户已寄回并回填物流，待商家收货
--     reshipped   商家已重新发货（换货线，待用户确认收货）
--     refunded    已退款（终态，退款线）
--     exchanged   换货完成（终态，换货线）
--     rejected    商家驳回（终态，可申请平台介入）
--     appealing   用户已申请平台介入，待平台仲裁
--     arbitrated  平台已仲裁（终态，裁定结果见 arbitrate_result）
--     closed      用户主动撤销（终态）
--   注：旧 DDL 注释里的 'approved' 是幽灵态，代码从未写入，本次不再使用。
--
--   幂等：使用 pm_add_col / pm_add_idx 存储过程包装，可重复执行。
--   执行：mysql -uroot -p petmate < migrate_v8_aftersale.sql
-- ============================================================

-- ---------- 幂等辅助存储过程 ----------
DROP PROCEDURE IF EXISTS pm_add_col;
DROP PROCEDURE IF EXISTS pm_add_idx;

DELIMITER $$

CREATE PROCEDURE pm_add_col(IN p_tbl VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_tbl AND COLUMN_NAME = p_col
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_tbl, '` ADD COLUMN ', p_ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

CREATE PROCEDURE pm_add_idx(IN p_tbl VARCHAR(64), IN p_idx VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_tbl AND INDEX_NAME = p_idx
  ) THEN
    SET @s = CONCAT('ALTER TABLE `', p_tbl, '` ADD INDEX `', p_idx, '` (', p_cols, ')');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

DELIMITER ;

-- ============================================================
-- 1. 补齐「历史手工加列」（有代码读写但从无 DDL，重建库必崩）
-- ============================================================
CALL pm_add_col('t_refund', 'refund_type',
  "`refund_type` VARCHAR(32) NOT NULL DEFAULT 'REFUND_ONLY' COMMENT '售后类型 REFUND_ONLY=仅退款 RETURN_REFUND=退货退款 EXCHANGE=换货'");
CALL pm_add_col('t_refund', 'evidence_images',
  "`evidence_images` TEXT DEFAULT NULL COMMENT '用户上传凭证图 URL，JSON 数组字符串'");
CALL pm_add_col('t_refund', 'logistics_no',
  "`logistics_no` VARCHAR(64) DEFAULT NULL COMMENT '用户寄回物流单号'");
CALL pm_add_col('t_refund', 'logistics_company',
  "`logistics_company` VARCHAR(64) DEFAULT NULL COMMENT '用户寄回物流公司'");
CALL pm_add_col('t_refund', 'updated_at',
  "`updated_at` DATETIME DEFAULT NULL COMMENT '更新时间'");

-- ============================================================
-- 2. 商家改金额：amount = 用户可退上限(下单实付)，refund_amount = 商家核定实退
-- ============================================================
CALL pm_add_col('t_refund', 'refund_amount',
  "`refund_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '商家核定实退金额；NULL 表示按 amount 全额退'");

-- ============================================================
-- 3. 退货 / 换货 链路字段
-- ============================================================
CALL pm_add_col('t_refund', 'return_address',
  "`return_address` VARCHAR(255) DEFAULT NULL COMMENT '商家同意退货/换货时提供的收件地址(含收件人与电话)'");
CALL pm_add_col('t_refund', 'shipped_at',
  "`shipped_at` DATETIME DEFAULT NULL COMMENT '用户回填寄回物流的时间'");
CALL pm_add_col('t_refund', 'new_logistics_no',
  "`new_logistics_no` VARCHAR(64) DEFAULT NULL COMMENT '换货：商家重发物流单号'");
CALL pm_add_col('t_refund', 'new_logistics_company',
  "`new_logistics_company` VARCHAR(64) DEFAULT NULL COMMENT '换货：商家重发物流公司'");
CALL pm_add_col('t_refund', 'reshipped_at',
  "`reshipped_at` DATETIME DEFAULT NULL COMMENT '换货：商家重新发货时间'");
CALL pm_add_col('t_refund', 'finished_at',
  "`finished_at` DATETIME DEFAULT NULL COMMENT '售后单流转到终态的时间'");

-- ============================================================
-- 4. 平台客服介入 / 仲裁字段
-- ============================================================
CALL pm_add_col('t_refund', 'prev_status',
  "`prev_status` VARCHAR(24) DEFAULT NULL COMMENT '申请介入前的状态，仲裁驳回时回退用'");
CALL pm_add_col('t_refund', 'appeal_reason',
  "`appeal_reason` VARCHAR(500) DEFAULT NULL COMMENT '用户申请平台介入的理由'");
CALL pm_add_col('t_refund', 'appeal_images',
  "`appeal_images` TEXT DEFAULT NULL COMMENT '申请介入补充凭证图 URL，JSON 数组字符串'");
CALL pm_add_col('t_refund', 'appealed_at',
  "`appealed_at` DATETIME DEFAULT NULL COMMENT '申请介入时间'");
CALL pm_add_col('t_refund', 'arbitrate_result',
  "`arbitrate_result` VARCHAR(16) DEFAULT NULL COMMENT '平台裁定结果 refund=支持退款 reject=维持驳回'");
CALL pm_add_col('t_refund', 'arbitrate_note',
  "`arbitrate_note` VARCHAR(500) DEFAULT NULL COMMENT '平台仲裁说明'");
CALL pm_add_col('t_refund', 'arbitrated_at',
  "`arbitrated_at` DATETIME DEFAULT NULL COMMENT '平台仲裁时间'");

-- ============================================================
-- 5. status 列扩宽：新状态 exchanging/arbitrated 长度 10，VARCHAR(16) 勉强够，
--    统一放宽到 24 留余量；同时刷新注释
-- ============================================================
ALTER TABLE t_refund
  MODIFY COLUMN `status` VARCHAR(24) NOT NULL DEFAULT 'pending'
  COMMENT 'pending/returning/exchanging/returned/reshipped/refunded/exchanged/rejected/appealing/arbitrated/closed';

ALTER TABLE t_refund
  MODIFY COLUMN `orig_status` VARCHAR(24) DEFAULT NULL
  COMMENT '申请售后前的订单状态，驳回时还原';

ALTER TABLE t_refund
  MODIFY COLUMN `reason` VARCHAR(500) DEFAULT NULL COMMENT '用户申请售后的说明';

ALTER TABLE t_refund
  MODIFY COLUMN `handle_note` VARCHAR(500) DEFAULT NULL COMMENT '商家/平台处理备注';

-- ---------- 索引 ----------
CALL pm_add_idx('t_refund', 'idx_user_status', '`user_id`, `status`');
CALL pm_add_idx('t_refund', 'idx_created',     '`created_at`');

-- ============================================================
-- 6. 售后操作流水表 —— 用户端「售后进度时间线」的数据源
--    每次状态流转写一条，前端直接倒序渲染即可，无需从字段反推
-- ============================================================
CREATE TABLE IF NOT EXISTS t_refund_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  refund_id   BIGINT       NOT NULL COMMENT '售后单 id',
  order_no    VARCHAR(32)  NOT NULL COMMENT '冗余订单号，便于排查',
  actor_type  VARCHAR(16)  NOT NULL COMMENT '操作方 user/merchant/admin/system',
  actor_id    BIGINT       DEFAULT NULL COMMENT '操作人 id',
  actor_name  VARCHAR(64)  DEFAULT NULL COMMENT '操作人展示名',
  action      VARCHAR(32)  NOT NULL COMMENT 'apply/approve/reject/ship/confirm/reship/receive/appeal/arbitrate/close',
  from_status VARCHAR(24)  DEFAULT NULL COMMENT '流转前状态',
  to_status   VARCHAR(24)  DEFAULT NULL COMMENT '流转后状态',
  note        VARCHAR(500) DEFAULT NULL COMMENT '节点说明，直接展示给用户',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_refund (refund_id),
  KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后操作流水（进度时间线）';

-- ============================================================
-- 7. 存量数据修正
-- ============================================================
-- 幽灵态 approved 归一到 pending（历史上从未写入，保险起见）
UPDATE t_refund SET status = 'pending' WHERE status = 'approved';
-- refund_amount 为空的历史单，按 amount 全额补齐，方便统一读取
UPDATE t_refund SET refund_amount = amount WHERE refund_amount IS NULL;

-- ---------- 清理辅助过程 ----------
DROP PROCEDURE IF EXISTS pm_add_col;
DROP PROCEDURE IF EXISTS pm_add_idx;

-- ---------- 校验 ----------
SELECT '=== t_refund 列 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_refund'
ORDER BY ORDINAL_POSITION;
SELECT '=== t_refund_log 是否存在 ===' AS info;
SELECT COUNT(*) AS log_table_exists FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_refund_log';
