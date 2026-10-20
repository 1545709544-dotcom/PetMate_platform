-- product view footprint (browsing history), per user
CREATE TABLE t_product_footprint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  view_count INT NOT NULL DEFAULT 1,
  last_view_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_product (user_id, product_id),
  KEY idx_user_last_view (user_id, last_view_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
