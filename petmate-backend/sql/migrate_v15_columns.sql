-- ============================================================================
-- 迁移 v15：补齐商品域三表缺失列，解除商品模块 500
--   1) t_product.specs / detail_images      (商品详情/商家上架/编辑引用)
--   2) t_product_sku.is_deleted             (购物车/下单/SKU管理引用)
--   3) t_product_review.reply / replied_at  (商品评价列表/商家回复引用)
-- 幂等：可重复执行。
-- 运行：mysql --default-character-set=utf8mb4 -h127.0.0.1 -uroot -p123456 petmate < sql/migrate_v15_columns.sql
-- ============================================================================

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

CALL pm_add_col('t_product', 'specs', "`specs` JSON NULL COMMENT '商品规格(扁平对象,如 {\"品牌\":\"x\",\"规格\":\"5kg\"})'");
CALL pm_add_col('t_product', 'detail_images', "`detail_images` JSON NULL COMMENT '详情页图片列表'");
CALL pm_add_col('t_product_sku', 'is_deleted', "`is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删(1=已删),编辑商品时非提交SKU软删'");
CALL pm_add_col('t_product_review', 'reply', "`reply` VARCHAR(500) NULL COMMENT '商家回复'");
CALL pm_add_col('t_product_review', 'replied_at', "`replied_at` DATETIME NULL COMMENT '商家回复时间'");

DROP PROCEDURE IF EXISTS pm_add_col;
