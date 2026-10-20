-- 新增：轮播 Banner 与平台公告表（P2 管理端配置补充）
-- 2026-07-31

CREATE TABLE IF NOT EXISTS t_banner (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(120) NOT NULL DEFAULT '' COMMENT 'banner 标题',
  image_url   VARCHAR(512) NOT NULL DEFAULT '' COMMENT '图片地址',
  link_url    VARCHAR(512) DEFAULT NULL COMMENT '跳转链接',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0下线 1上线',
  start_time  DATETIME     DEFAULT NULL,
  end_time    DATETIME     DEFAULT NULL,
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轮播 Banner 配置';

CREATE TABLE IF NOT EXISTS t_announcement (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(160) NOT NULL DEFAULT '' COMMENT '公告标题',
  content      TEXT         COMMENT '公告正文',
  cover        VARCHAR(512) DEFAULT NULL COMMENT '封面图',
  target       VARCHAR(32)  NOT NULL DEFAULT 'all' COMMENT '推送对象 all/user/merchant',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布(已推送)',
  sort         INT          NOT NULL DEFAULT 0,
  is_deleted   TINYINT      NOT NULL DEFAULT 0,
  published_at DATETIME     DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告';

-- 可选种子数据（演示用，可注释跳过）
INSERT INTO t_banner (title, image_url, link_url, sort, status)
VALUES ('欢迎使用 PetMate 智能宠物管家', '/static/uploads/product/banner-demo.jpg', '/', 1, 1);
