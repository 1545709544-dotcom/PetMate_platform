-- 用户侧消息通知表（前台"我的消息"）：举报处置后向被举报内容作者推送违规警告
-- 2026-07-30 新增，配合举报处理"删除内容 + 警告作者"功能
CREATE TABLE IF NOT EXISTS t_user_notification (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL COMMENT '接收通知的用户',
  type        VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT 'warning=违规警告 / system=系统通知',
  title       VARCHAR(128) NOT NULL COMMENT '通知标题',
  content     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '通知正文',
  related_id  VARCHAR(64)  DEFAULT NULL COMMENT '关联业务 id（如举报 id）',
  is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_unread (user_id, is_read),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知';

-- 测试时可插入一条示例，便于前台核对（非必须）
-- INSERT INTO t_user_notification (user_id, type, title, content, related_id)
-- VALUES (10003, 'warning', '内容违规下架通知', '您的帖子因被举报违反社区规范已被下架，请遵守社区公约。', '1');
