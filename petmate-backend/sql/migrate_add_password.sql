-- 2026-07-20 新增 password 列（支持真实注册）
ALTER TABLE t_user ADD COLUMN password VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt hash, NULL means use demo pwd 123456';
