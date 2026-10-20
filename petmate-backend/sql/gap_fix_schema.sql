-- ============================================================
-- PetMate 功能补全：补齐缺失表/字段
-- 1) 文章点赞 t_article_like + t_article.like_count
-- 2) 宠物成长日记照片 t_pet_photo
-- 说明：演示库可直接重复执行（已带 IF NOT EXISTS / 列存在判断）
-- ============================================================

USE petmate;

-- ---------- 1. 文章点赞 ----------
CREATE TABLE IF NOT EXISTS t_article_like (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    article_id  BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_user (article_id, user_id),
    KEY idx_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- t_article 增加 like_count 列（不存在才加）
SET @exist := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = 'petmate' AND table_name = 't_article' AND column_name = 'like_count'
);
SET @sql := IF(@exist = 0,
    'ALTER TABLE t_article ADD COLUMN like_count INT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 用已有点赞关系回填 like_count（首次执行时若 t_article_like 为空则无影响）
UPDATE t_article a
SET a.like_count = (
    SELECT COUNT(*) FROM t_article_like l WHERE l.article_id = a.id AND l.is_deleted = 0
)
WHERE a.is_deleted = 0;

-- ---------- 2. 宠物成长日记照片 ----------
CREATE TABLE IF NOT EXISTS t_pet_photo (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    pet_id      BIGINT       NOT NULL,
    url         VARCHAR(512) NOT NULL,
    caption     VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pet (pet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT 'gap_fix_schema done' AS result;
