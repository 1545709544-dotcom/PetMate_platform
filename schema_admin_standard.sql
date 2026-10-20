-- ============================================================
-- 商家后台标准化：消息通知中心 / 操作审计 / RBAC 员工
-- ============================================================

-- 1) 消息通知中心
CREATE TABLE IF NOT EXISTS t_merchant_notification (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT       NOT NULL,
  type        VARCHAR(32)  NOT NULL DEFAULT 'system',
  title       VARCHAR(128) NOT NULL,
  content     VARCHAR(512),
  related_id  VARCHAR(64),
  is_read     TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_merchant (merchant_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 操作审计日志
CREATE TABLE IF NOT EXISTS t_operation_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id   BIGINT       NOT NULL,
  operator_id   BIGINT       NOT NULL,
  operator_name VARCHAR(64),
  role          VARCHAR(32),
  module        VARCHAR(32)  NOT NULL,
  action        VARCHAR(64)  NOT NULL,
  target        VARCHAR(128),
  detail        VARCHAR(512),
  ip            VARCHAR(64),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_merchant (merchant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) RBAC 员工表（子账号）
CREATE TABLE IF NOT EXISTS t_merchant_staff (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id  BIGINT      NOT NULL,
  username     VARCHAR(64) NOT NULL,
  password     VARCHAR(128) NOT NULL,
  nickname     VARCHAR(64),
  role         VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
  status       TINYINT     NOT NULL DEFAULT 1,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_username (merchant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
