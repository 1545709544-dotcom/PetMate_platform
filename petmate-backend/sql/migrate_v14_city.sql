-- 迁移：为「用户」与「社区帖子」增加城市(city)字段
-- 用途：社区「附近/市区」分类按用户所在城市筛选；发布动态时可「添加地点」。
-- 幂等：先判断字段是否存在，避免重复执行报错。

SET @db = DATABASE();

-- t_user.city
SET @col_user = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'city'
);
SET @sql_user = IF(@col_user = 0,
  'ALTER TABLE t_user ADD COLUMN city VARCHAR(64) DEFAULT NULL COMMENT ''所在城市(如 北京)，用于社区附近分类''',
  'SELECT 1');
PREPARE stmt FROM @sql_user;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- t_post.city
SET @col_post = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_post' AND COLUMN_NAME = 'city'
);
SET @sql_post = IF(@col_post = 0,
  'ALTER TABLE t_post ADD COLUMN city VARCHAR(64) DEFAULT NULL COMMENT ''发布所在城市，用于社区附近分类''',
  'SELECT 1');
PREPARE stmt FROM @sql_post;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
