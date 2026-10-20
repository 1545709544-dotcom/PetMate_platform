-- ============================================================
-- PetMate v17 增量迁移脚本
-- 1) 补齐 t_merchant_staff DDL（仓库历史缺失,代码全量使用）
-- 2) 新增 t_token_blacklist（TokenBlacklist 持久化,R3.13/R3.14）
-- ============================================================
-- 此脚本可重跑（所有 CREATE 均带存在性判断）
-- 建议独立执行：mysql -uroot -p123456 petmate < migrate_v17.sql
-- ============================================================

-- ============= t_merchant_staff 员工表 DDL（补齐） =============
-- 字段定义来源于 dev 库导出（SHOW CREATE TABLE t_merchant_staff）。
-- status 语义：1=启用 0=停用（注意与 t_user.status 的 0/1/2 含义不同）。
CREATE TABLE IF NOT EXISTS t_merchant_staff (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT       NOT NULL COMMENT '所属店主 user.id（t_user 主键）',
  username    VARCHAR(64)  NOT NULL,
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
  nickname    VARCHAR(64)  DEFAULT NULL,
  role        VARCHAR(32)  NOT NULL DEFAULT 'OPERATOR' COMMENT 'OWNER/OPERATOR/CS/WAREHOUSE',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_username (merchant_id, username),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家员工子账号';

-- ============= t_token_blacklist JWT 黑名单（R3.13/R3.14 持久化） =============
-- 写入时机：/auth/logout、管理员封禁账号、安全事件全局撤销
-- 读取时机：JwtAuthFilter 解析 token 后查 jti
-- 清理策略：每 30 分钟异步删 exp_at < NOW() 的过期条目
CREATE TABLE IF NOT EXISTS t_token_blacklist (
  jti         VARCHAR(64)  NOT NULL COMMENT 'JWT 中的 jti claim（UUID）',
  user_id     BIGINT       NOT NULL DEFAULT 0 COMMENT 'token 所属用户 id（便于按用户批量撤销）',
  exp_at      DATETIME     NOT NULL COMMENT 'token 原过期时间',
  revoked_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入黑名单时间',
  reason      VARCHAR(32)  DEFAULT NULL COMMENT 'logout/ban/security',
  PRIMARY KEY (jti),
  KEY idx_exp_at (exp_at),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JWT 撤销黑名单';

-- ============================================================
-- 验证
-- ============================================================
SELECT 't_merchant_staff' AS tbl, COUNT(*) AS cnt FROM t_merchant_staff
UNION ALL SELECT 't_token_blacklist', COUNT(*) FROM t_token_blacklist;