-- v18: 领养信息增加发布者归属 user_id，支撑"用户个人领养数据管理页"
-- 历史数据无归属，统一回填到演示账号 90005（13762522534），便于查看"我的领养"

ALTER TABLE t_adoption
  ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT '发布者用户id（NULL=系统/历史匿名）' AFTER contact;

ALTER TABLE t_adoption
  ADD KEY idx_user (user_id);

UPDATE t_adoption
   SET user_id = 90005
 WHERE user_id IS NULL AND is_deleted = 0;
