-- ============================================================
-- 迁移:把头像列从 VARCHAR(255) 改为 MEDIUMTEXT,
-- 解决前端 base64 上传整张图片超过 255 字节导致的
-- "Data too long for column 'avatar' at row 1" 错误。
--
-- 影响表:
--   t_pet.avatar
--   t_user.avatar (用户头像同问题)
--   t_post.user_avatar
--   t_product_review.user_avatar
--   t_consult_record.user_avatar (如存在)
-- ============================================================

ALTER TABLE t_pet             MODIFY COLUMN avatar      MEDIUMTEXT DEFAULT NULL;
ALTER TABLE t_user            MODIFY COLUMN avatar      MEDIUMTEXT DEFAULT NULL;
ALTER TABLE t_post            MODIFY COLUMN user_avatar MEDIUMTEXT DEFAULT NULL;
ALTER TABLE t_product_review  MODIFY COLUMN user_avatar MEDIUMTEXT DEFAULT NULL;
-- 同时把评论/评价里的头像也拓宽,避免后续 500
ALTER TABLE t_post_comment    MODIFY COLUMN user_avatar MEDIUMTEXT DEFAULT NULL;
ALTER TABLE t_review          MODIFY COLUMN user_avatar MEDIUMTEXT DEFAULT NULL;
