-- 爱心捐助排行奖励记录表
-- 每个用户对每个档位(tier)只能领取一次（唯一键约束保证幂等）
CREATE TABLE t_donate_reward (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  tier VARCHAR(20) NOT NULL,
  reward_points INT NOT NULL,
  total_donate DECIMAL(10,2) NOT NULL,
  claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tier (user_id, tier),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
