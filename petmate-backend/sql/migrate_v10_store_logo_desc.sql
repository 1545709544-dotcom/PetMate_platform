-- migrate_v10: 店铺 logo 与简介（P2 商家端「设置」补全）
-- 2026-07-31
ALTER TABLE t_store ADD COLUMN logo VARCHAR(255) DEFAULT NULL COMMENT '店铺Logo图片URL' AFTER photos;
ALTER TABLE t_store ADD COLUMN description VARCHAR(500) DEFAULT NULL COMMENT '店铺简介' AFTER logo;
