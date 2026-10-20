CREATE TABLE t_vip_pay (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  out_trade_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  plan_level INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending_pay',
  trade_no VARCHAR(64) DEFAULT NULL,
  paid_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
