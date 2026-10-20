-- ============================================================================
-- 服务模块行业化升级 (v13)
-- 对标市面宠物服务平台(东森宠物云 / 极有爱 / 小捲尾 / 美团宠物)的服务体系：
--   1) 洗护美容按「体型 × 毛长」分档定价，时长随档位变化  -> t_service_spec
--   2) 剪指甲 / 挤肛门腺 / 药浴 / 拆结费 等单项加购        -> t_service_addon
--   3) 服务包含项、预约须知、退改政策                      -> t_service_item 扩展
--   4) 按营业时段切片 + 每时段容量，避免撞单                -> t_service_item 扩展
--   5) 到店 / 上门(含上门费)、适用宠物(犬/猫)              -> t_service_item 扩展
--   6) 服务完成后评价，回写评分与销量                      -> t_service_review
--   7) 预约单记录规格/加购/宠物/联系人/金额/核销码          -> t_appointment 扩展
--
-- 幂等：可重复执行。
-- 运行：mysql --default-character-set=utf8mb4 -h127.0.0.1 -uroot -p123456 petmate < sql/migrate_v13_service_upgrade.sql
-- ============================================================================

-- ---------- 幂等加列辅助过程 ----------
DROP PROCEDURE IF EXISTS pm_add_col;
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
DELIMITER ;

-- ============================================================================
-- 1. t_service_item 扩展
-- ============================================================================
CALL pm_add_col('t_service_item', 'sub_category',   "`sub_category` VARCHAR(32) NULL COMMENT '二级分类,如 精致洗护/短期寄养'");
CALL pm_add_col('t_service_item', 'service_mode',   "`service_mode` VARCHAR(16) NOT NULL DEFAULT 'instore' COMMENT 'instore到店 / onsite上门'");
CALL pm_add_col('t_service_item', 'pet_type',       "`pet_type` VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT '适用宠物 dog/cat/all'");
CALL pm_add_col('t_service_item', 'original_price', "`original_price` DECIMAL(10,2) NULL COMMENT '划线原价'");
CALL pm_add_col('t_service_item', 'onsite_fee',     "`onsite_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '上门服务费'");
CALL pm_add_col('t_service_item', 'sales',          "`sales` INT NOT NULL DEFAULT 0 COMMENT '累计成交量'");
CALL pm_add_col('t_service_item', 'rating',         "`rating` DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '平均评分'");
CALL pm_add_col('t_service_item', 'review_count',   "`review_count` INT NOT NULL DEFAULT 0 COMMENT '评价数'");
CALL pm_add_col('t_service_item', 'tags',           "`tags` VARCHAR(255) NULL COMMENT '卖点标签,逗号分隔'");
CALL pm_add_col('t_service_item', 'include_items',  "`include_items` TEXT NULL COMMENT '服务包含项,换行分隔'");
CALL pm_add_col('t_service_item', 'notice',         "`notice` TEXT NULL COMMENT '预约须知,换行分隔'");
CALL pm_add_col('t_service_item', 'refund_policy',  "`refund_policy` VARCHAR(255) NULL COMMENT '退改政策'");
CALL pm_add_col('t_service_item', 'open_time',      "`open_time` VARCHAR(5) NOT NULL DEFAULT '09:00' COMMENT '可约开始时间'");
CALL pm_add_col('t_service_item', 'close_time',     "`close_time` VARCHAR(5) NOT NULL DEFAULT '20:00' COMMENT '可约结束时间'");
CALL pm_add_col('t_service_item', 'slot_minutes',   "`slot_minutes` INT NOT NULL DEFAULT 60 COMMENT '时段粒度(分钟)'");
CALL pm_add_col('t_service_item', 'slot_capacity',  "`slot_capacity` INT NOT NULL DEFAULT 2 COMMENT '每时段可接单数'");
CALL pm_add_col('t_service_item', 'advance_days',   "`advance_days` INT NOT NULL DEFAULT 7 COMMENT '最多可提前预约天数'");
CALL pm_add_col('t_service_item', 'updated_at',     "`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

-- ============================================================================
-- 2. t_appointment 扩展
-- ============================================================================
CALL pm_add_col('t_appointment', 'spec_id',       "`spec_id` BIGINT NULL COMMENT '规格档位 id'");
CALL pm_add_col('t_appointment', 'spec_name',     "`spec_name` VARCHAR(64) NULL COMMENT '规格档位名(冗余)'");
CALL pm_add_col('t_appointment', 'addon_ids',     "`addon_ids` VARCHAR(128) NULL COMMENT '加购项 id,逗号分隔'");
CALL pm_add_col('t_appointment', 'addon_names',   "`addon_names` VARCHAR(255) NULL COMMENT '加购项名称(冗余)'");
CALL pm_add_col('t_appointment', 'pet_id',        "`pet_id` BIGINT NULL COMMENT '服务宠物 id'");
CALL pm_add_col('t_appointment', 'pet_name',      "`pet_name` VARCHAR(64) NULL COMMENT '服务宠物名(冗余)'");
CALL pm_add_col('t_appointment', 'service_mode',  "`service_mode` VARCHAR(16) NOT NULL DEFAULT 'instore' COMMENT 'instore到店 / onsite上门'");
CALL pm_add_col('t_appointment', 'address',       "`address` VARCHAR(255) NULL COMMENT '上门服务地址'");
CALL pm_add_col('t_appointment', 'contact_name',  "`contact_name` VARCHAR(64) NULL COMMENT '联系人'");
CALL pm_add_col('t_appointment', 'contact_phone', "`contact_phone` VARCHAR(20) NULL COMMENT '联系电话'");
CALL pm_add_col('t_appointment', 'base_amount',   "`base_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '规格基础价'");
CALL pm_add_col('t_appointment', 'addon_amount',  "`addon_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '加购小计'");
CALL pm_add_col('t_appointment', 'onsite_fee',    "`onsite_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '上门费'");
CALL pm_add_col('t_appointment', 'amount',        "`amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付总额(到店支付)'");
CALL pm_add_col('t_appointment', 'days',          "`days` INT NOT NULL DEFAULT 1 COMMENT '寄养天数(按天计价类服务)'");
CALL pm_add_col('t_appointment', 'duration',      "`duration` INT NOT NULL DEFAULT 60 COMMENT '预计服务时长(分钟)'");
CALL pm_add_col('t_appointment', 'end_time',      "`end_time` DATETIME NULL COMMENT '预计结束时间'");
CALL pm_add_col('t_appointment', 'verify_code',   "`verify_code` VARCHAR(16) NULL COMMENT '到店核销码'");
CALL pm_add_col('t_appointment', 'cancel_reason', "`cancel_reason` VARCHAR(128) NULL COMMENT '取消原因'");
CALL pm_add_col('t_appointment', 'reviewed',      "`reviewed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已评价'");

-- ============================================================================
-- 3. 新表：服务规格档位（行业核心：体型 × 毛长 分档定价）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `t_service_spec` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `service_id` BIGINT NOT NULL,
  `name`       VARCHAR(64) NOT NULL COMMENT '档位名,如 小型犬 3-5kg',
  `pet_kind`   VARCHAR(16) NOT NULL DEFAULT 'dog' COMMENT 'dog/cat/any',
  `coat`       VARCHAR(16) NOT NULL DEFAULT 'any' COMMENT '毛长 short/long/any',
  `weight_min` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '体重下限(kg,含)',
  `weight_max` DECIMAL(5,2) NOT NULL DEFAULT 999.00 COMMENT '体重上限(kg,不含)',
  `price`      DECIMAL(10,2) NOT NULL,
  `duration`   INT NOT NULL DEFAULT 60 COMMENT '该档位服务时长(分钟)',
  `sort_no`    INT NOT NULL DEFAULT 0,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_service` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务规格档位';

-- ============================================================================
-- 4. 新表：服务加购项（剪指甲 / 挤肛门腺 / 药浴 / 拆结费…）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `t_service_addon` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `service_id`  BIGINT NOT NULL,
  `name`        VARCHAR(64) NOT NULL,
  `price`       DECIMAL(10,2) NOT NULL,
  `duration`    INT NOT NULL DEFAULT 0 COMMENT '额外耗时(分钟)',
  `description` VARCHAR(128) NULL,
  `sort_no`     INT NOT NULL DEFAULT 0,
  `is_deleted`  TINYINT NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_service` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务加购项';

-- ============================================================================
-- 5. 新表：服务评价（完成后可评，回写 service.rating / review_count）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `t_service_review` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT,
  `service_id`     BIGINT NOT NULL,
  `store_id`       BIGINT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `user_id`        BIGINT NOT NULL,
  `rating`         TINYINT NOT NULL DEFAULT 5,
  `content`        VARCHAR(500) NULL,
  `images`         VARCHAR(1000) NULL COMMENT '图片 url,逗号分隔',
  `spec_name`      VARCHAR(64) NULL COMMENT '下单档位(冗余,便于展示"小型犬 3-5kg")',
  `reply`          VARCHAR(500) NULL COMMENT '商家回复',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment` (`appointment_id`),
  KEY `idx_service` (`service_id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务评价';

-- ============================================================================
-- 6. 历史数据迁移：一级分类收敛为标准 6 类，原「上门遛狗」降为二级
-- ============================================================================
UPDATE t_service_item SET sub_category = '上门遛狗', category = '上门服务', service_mode = 'onsite'
  WHERE category = '上门遛狗';
UPDATE t_appointment SET category = '上门服务' WHERE category = '上门遛狗';

UPDATE t_service_item SET sub_category = IFNULL(sub_category, '精致洗护') WHERE category = '美容洗护';
UPDATE t_service_item SET sub_category = IFNULL(sub_category, '短期寄养') WHERE category = '寄养看护';
UPDATE t_service_item SET sub_category = IFNULL(sub_category, '基础服从') WHERE category = '行为训练';
UPDATE t_service_item SET sub_category = IFNULL(sub_category, '健康体检') WHERE category = '宠物医疗';
UPDATE t_service_item SET sub_category = IFNULL(sub_category, '综合服务') WHERE category = '综合服务';

-- 寄养按天计价，时段粒度放宽；上门服务默认收上门费
UPDATE t_service_item SET slot_minutes = 60, slot_capacity = 4, advance_days = 30 WHERE category = '寄养看护';
UPDATE t_service_item SET onsite_fee = 10.00 WHERE service_mode = 'onsite' AND onsite_fee = 0;

-- 清理明显的联调测试残留
UPDATE t_service_item SET is_deleted = 1 WHERE name = 'VerifySvc';

-- 已有预约回填冗余金额/时长，避免历史单展示 ¥0
UPDATE t_appointment a JOIN t_service_item s ON a.service_id = s.id
   SET a.base_amount = s.price, a.amount = s.price, a.duration = s.duration
 WHERE a.amount = 0;

DROP PROCEDURE IF EXISTS pm_add_col;
