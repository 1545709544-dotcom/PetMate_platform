-- PetMate 数据库初始化
-- 参考 PRD §6：用户/宠物/商品/交易/服务/AI/内容/营销/推荐风控 9 域
-- MySQL 8.0 / utf8mb4

CREATE DATABASE IF NOT EXISTS petmate
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE petmate;

-- ============================================================
-- 用户域
-- ============================================================
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  open_id         VARCHAR(64)  DEFAULT NULL,
  phone           VARCHAR(20)  DEFAULT NULL,
  nickname        VARCHAR(64)  NOT NULL DEFAULT '萌宠家长',
  avatar          MEDIUMTEXT   DEFAULT 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',
  gender          TINYINT      NOT NULL DEFAULT 0  COMMENT '0未知/1男/2女',
  birthday        DATE         DEFAULT NULL,
  vip_level       TINYINT      NOT NULL DEFAULT 0,
  bio             VARCHAR(255) DEFAULT NULL,
  password        VARCHAR(255) DEFAULT NULL COMMENT 'BCrypt 哈希;NULL 表示未设置密码(走演示口令 123456)',
  status          TINYINT      NOT NULL DEFAULT 0  COMMENT '0正常/1封禁/2注销',
  role            VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT '账号角色: user/merchant/admin (登录鉴权唯一依据,禁止再用手机号前缀推断)',
  vip_expire_at   DATETIME     DEFAULT NULL,
  real_name       VARCHAR(64)  DEFAULT NULL COMMENT '实名认证姓名',
  id_card         VARCHAR(32)  DEFAULT NULL COMMENT '实名认证身份证号',
  verified        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未实名/1已实名',
  region          VARCHAR(128) DEFAULT NULL COMMENT '所在地区(省/市/区)',
  city            VARCHAR(64)  DEFAULT NULL COMMENT '所在城市,社区「同城」分类用',
  last_login_at   DATETIME     DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone),
  UNIQUE KEY uk_open_id (open_id),
  KEY idx_vip_level (vip_level),
  KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

DROP TABLE IF EXISTS t_user_address;
CREATE TABLE t_user_address (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  receiver    VARCHAR(64) NOT NULL,
  phone       VARCHAR(20) NOT NULL,
  province    VARCHAR(32) NOT NULL,
  city        VARCHAR(32) NOT NULL,
  district    VARCHAR(32) NOT NULL,
  detail      VARCHAR(255) NOT NULL,
  is_default  TINYINT     NOT NULL DEFAULT 0,
  tag         VARCHAR(32) DEFAULT 'home',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

DROP TABLE IF EXISTS t_favorite;
CREATE TABLE t_favorite (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  target_type VARCHAR(16) NOT NULL COMMENT 'product/post/article/service',
  target_id   BIGINT      NOT NULL,
  title       VARCHAR(255) DEFAULT NULL,
  image       VARCHAR(255) DEFAULT NULL,
  price       DECIMAL(10,2) DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_target (user_id, target_type, target_id, is_deleted),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

DROP TABLE IF EXISTS t_coupon;
CREATE TABLE t_coupon (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  type        VARCHAR(16) NOT NULL COMMENT 'reduction/discount/category',
  threshold   DECIMAL(10,2) NOT NULL DEFAULT 0,
  amount      DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount    DECIMAL(3,2)  DEFAULT NULL,
  valid_start DATETIME    NOT NULL,
  valid_end   DATETIME    NOT NULL,
  status      TINYINT     NOT NULL DEFAULT 1,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

DROP TABLE IF EXISTS t_user_coupon;
CREATE TABLE t_user_coupon (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  coupon_id   BIGINT      NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'available' COMMENT 'available/used/expired',
  claim_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at     DATETIME    DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- ============================================================
-- 宠物域
-- ============================================================
DROP TABLE IF EXISTS t_pet_category;
CREATE TABLE t_pet_category (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(32) NOT NULL,
  icon       VARCHAR(255) DEFAULT NULL,
  sort       INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物种类';

DROP TABLE IF EXISTS t_pet_breed;
CREATE TABLE t_pet_breed (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  category_id BIGINT      NOT NULL,
  name        VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物品种';

DROP TABLE IF EXISTS t_pet;
CREATE TABLE t_pet (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  name        VARCHAR(64) NOT NULL,
  category_id BIGINT      NOT NULL,
  breed_id    BIGINT      NOT NULL,
  gender      TINYINT     NOT NULL DEFAULT 2 COMMENT '0公/1母/2未知',
  birthday    DATE        DEFAULT NULL,
  weight      DECIMAL(5,2) DEFAULT NULL,
  neutered    TINYINT     NOT NULL DEFAULT 0,
  avatar      MEDIUMTEXT   DEFAULT NULL,
  tags        JSON        DEFAULT NULL,
  description TEXT        DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物档案';

DROP TABLE IF EXISTS t_pet_health_record;
CREATE TABLE t_pet_health_record (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  pet_id      BIGINT      NOT NULL,
  record_type VARCHAR(16) NOT NULL COMMENT 'vaccine/deworming/checkup/weight/illness',
  title       VARCHAR(64) NOT NULL,
  content     TEXT        DEFAULT NULL,
  record_date DATE        NOT NULL,
  next_date   DATE        DEFAULT NULL,
  reminder    TINYINT     NOT NULL DEFAULT 0,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pet (pet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宠物健康记录';

-- ============================================================
-- 商品域
-- ============================================================
DROP TABLE IF EXISTS t_product_category;
CREATE TABLE t_product_category (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  parent_id BIGINT      NOT NULL DEFAULT 0,
  name      VARCHAR(32) NOT NULL,
  icon      VARCHAR(255) DEFAULT NULL,
  level     TINYINT     NOT NULL DEFAULT 1,
  sort      INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  name         VARCHAR(255) NOT NULL,
  category_id  BIGINT      NOT NULL,
  brand_id     BIGINT      DEFAULT NULL,
  merchant_id  BIGINT      DEFAULT NULL,
  main_image   VARCHAR(255) NOT NULL,
  sub_images   JSON        DEFAULT NULL,
  detail       LONGTEXT    DEFAULT NULL,
  status       TINYINT     NOT NULL DEFAULT 1 COMMENT '0下架/1上架',
  sale_count   INT         NOT NULL DEFAULT 0,
  view_count   INT         NOT NULL DEFAULT 0,
  rating       DECIMAL(3,2) NOT NULL DEFAULT 5.00,
  sale_price   DECIMAL(10,2) NOT NULL,
  original_price DECIMAL(10,2) DEFAULT NULL,
  tags         JSON        DEFAULT NULL,
  specs        JSON        DEFAULT NULL COMMENT '商品规格(扁平对象,如 {"品牌":"x","规格":"5kg"})',
  detail_images JSON        DEFAULT NULL COMMENT '详情页图片列表',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted   TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_category (category_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SPU';

DROP TABLE IF EXISTS t_product_sku;
CREATE TABLE t_product_sku (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  product_id  BIGINT      NOT NULL,
  sku_code    VARCHAR(64) NOT NULL,
  spec_values JSON        DEFAULT NULL,
  price       DECIMAL(10,2) NOT NULL,
  stock       INT         NOT NULL DEFAULT 0,
  image       VARCHAR(255) DEFAULT NULL,
  is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '软删(1=已删)',
  PRIMARY KEY (id),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU';

DROP TABLE IF EXISTS t_product_review;
CREATE TABLE t_product_review (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  product_id    BIGINT      NOT NULL,
  order_id      BIGINT      DEFAULT NULL COMMENT '关联订单，用于每单每商品去重',
  user_id       BIGINT      NOT NULL,
  user_nickname VARCHAR(64) DEFAULT NULL,
  user_avatar   MEDIUMTEXT   DEFAULT NULL,
  rating        TINYINT     NOT NULL DEFAULT 5 COMMENT '1-5 星',
  content       TEXT        DEFAULT NULL,
  images        JSON        DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reply         VARCHAR(500) DEFAULT NULL COMMENT '商家回复',
  replied_at    DATETIME    DEFAULT NULL COMMENT '商家回复时间',
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product (product_id),
  KEY idx_order (order_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

-- ============================================================
-- 交易域
-- ============================================================
DROP TABLE IF EXISTS t_cart;
CREATE TABLE t_cart (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  sku_id      BIGINT      NOT NULL,
  product_id  BIGINT      NOT NULL,
  quantity    INT         NOT NULL DEFAULT 1,
  selected    TINYINT     NOT NULL DEFAULT 1,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sku (user_id, sku_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  order_no          VARCHAR(32) NOT NULL,
  user_id           BIGINT      NOT NULL,
  store_id          BIGINT      DEFAULT NULL,
  total_amount      DECIMAL(10,2) NOT NULL,
  pay_amount        DECIMAL(10,2) NOT NULL,
  freight_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
  coupon_id         BIGINT      DEFAULT NULL,
  coupon_amount     DECIMAL(10,2) DEFAULT 0,
  donate_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '爱心捐赠金额',
  status            VARCHAR(16) NOT NULL DEFAULT 'pending_pay'
                    COMMENT 'pending_pay/paid/shipping/done/cancelled/refunding',
  receiver_snapshot JSON        DEFAULT NULL,
  paid_at           DATETIME    DEFAULT NULL,
  delivered_at      DATETIME    DEFAULT NULL,
  finished_at       DATETIME    DEFAULT NULL,
  remark            VARCHAR(255) DEFAULT NULL,
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted        TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  order_id      BIGINT      NOT NULL,
  product_id    BIGINT      NOT NULL,
  sku_id        BIGINT      NOT NULL,
  product_name  VARCHAR(255) NOT NULL,
  product_image VARCHAR(255) DEFAULT NULL,
  price         DECIMAL(10,2) NOT NULL,
  quantity      INT         NOT NULL,
  snapshot_json JSON        DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品';

-- ============================================================
-- 商家域
-- ============================================================
DROP TABLE IF EXISTS t_store;
CREATE TABLE t_store (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  merchant_id    BIGINT      NOT NULL,
  name           VARCHAR(64) NOT NULL,
  type           VARCHAR(16) NOT NULL DEFAULT 'comprehensive',
  address        VARCHAR(255) NOT NULL,
  lng            DECIMAL(10,6) DEFAULT 0,
  lat            DECIMAL(10,6) DEFAULT 0,
  business_hours JSON        DEFAULT NULL,
  photos         JSON        DEFAULT NULL,
  logo           VARCHAR(255) DEFAULT NULL COMMENT '店铺Logo图片URL',
  description     VARCHAR(500) DEFAULT NULL COMMENT '店铺简介',
  audit_status   TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审核/1通过/2驳回',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店';

DROP TABLE IF EXISTS t_appointment;
CREATE TABLE t_appointment (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  user_id          BIGINT      NOT NULL,
  service_id       BIGINT      NOT NULL,
  service_name     VARCHAR(128) DEFAULT NULL,
  store_id         BIGINT      DEFAULT NULL,
  store_name       VARCHAR(128) DEFAULT NULL,
  category         VARCHAR(32) DEFAULT NULL,
  appointment_time DATETIME    NOT NULL,
  note             VARCHAR(200) DEFAULT NULL,
  status           VARCHAR(16) NOT NULL DEFAULT 'pending',
  create_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务预约';

DROP TABLE IF EXISTS t_service_item;
CREATE TABLE t_service_item (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  store_id    BIGINT      NOT NULL,
  name        VARCHAR(128) NOT NULL,
  category    VARCHAR(32) NOT NULL,
  price       DECIMAL(10,2) NOT NULL,
  duration    INT         NOT NULL DEFAULT 60,
  cover       VARCHAR(255) DEFAULT NULL,
  description TEXT        DEFAULT NULL,
  status      TINYINT     NOT NULL DEFAULT 1,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务项';

DROP TABLE IF EXISTS t_review;
CREATE TABLE t_review (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  store_id      BIGINT      NOT NULL,
  order_id      BIGINT      DEFAULT NULL,
  user_id       BIGINT      NOT NULL,
  user_nickname VARCHAR(64) DEFAULT NULL,
  user_avatar   MEDIUMTEXT   DEFAULT NULL,
  rating        DECIMAL(2,1) NOT NULL DEFAULT 5.0,
  content       TEXT        DEFAULT NULL,
  images        JSON        DEFAULT NULL,
  reply         TEXT        DEFAULT NULL,
  replied_at    DATETIME    DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务评价';

-- ============================================================
-- AI 问诊域
-- ============================================================
DROP TABLE IF EXISTS t_consultation;
CREATE TABLE t_consultation (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL,
  pet_id        BIGINT      DEFAULT NULL,
  title         VARCHAR(255) DEFAULT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'active',
  risk_level    VARCHAR(16) NOT NULL DEFAULT 'normal',
  message_count INT         NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at     DATETIME    DEFAULT NULL,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问诊会话';

DROP TABLE IF EXISTS t_consultation_message;
CREATE TABLE t_consultation_message (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  consultation_id BIGINT      NOT NULL,
  role            VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
  content         TEXT        NOT NULL,
  risk_level      VARCHAR(16) DEFAULT NULL,
  products        JSON        DEFAULT NULL,
  images          LONGTEXT    DEFAULT NULL COMMENT '用户上传图片 base64 data URL 列表(JSON数组)',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_consult (consultation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问诊消息';

-- ============================================================
-- 内容域
-- ============================================================
DROP TABLE IF EXISTS t_topic;
CREATE TABLE t_topic (
  id    BIGINT      NOT NULL AUTO_INCREMENT,
  name  VARCHAR(64) NOT NULL,
  icon  VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题';

DROP TABLE IF EXISTS t_post;
CREATE TABLE t_post (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  user_id         BIGINT      NOT NULL,
  user_nickname   VARCHAR(64) DEFAULT NULL,
  user_avatar     MEDIUMTEXT   DEFAULT NULL,
  content         TEXT        NOT NULL,
  images          JSON        DEFAULT NULL,
  video_url       VARCHAR(255) DEFAULT NULL,
  topic_id        BIGINT      DEFAULT NULL,
  topic_name      VARCHAR(64) DEFAULT NULL,
  like_count      INT         NOT NULL DEFAULT 0,
  comment_count   INT         NOT NULL DEFAULT 0,
  collect_count   INT         NOT NULL DEFAULT 0,
  visible         VARCHAR(16) NOT NULL DEFAULT 'public',
  audit_status    VARCHAR(16) NOT NULL DEFAULT 'approved' COMMENT 'pending/approved/rejected',
  audit_reason    VARCHAR(255) DEFAULT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子';

DROP TABLE IF EXISTS t_post_comment;
CREATE TABLE t_post_comment (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  post_id       BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  user_nickname VARCHAR(64) DEFAULT NULL,
  user_avatar   MEDIUMTEXT   DEFAULT NULL,
  content       TEXT        NOT NULL,
  like_count    INT         NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子评论';

DROP TABLE IF EXISTS t_post_like;
CREATE TABLE t_post_like (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  post_id     BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞';

DROP TABLE IF EXISTS t_post_collect;
CREATE TABLE t_post_collect (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  post_id     BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏';

DROP TABLE IF EXISTS t_article;
CREATE TABLE t_article (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  category_id BIGINT      NOT NULL,
  title       VARCHAR(255) NOT NULL,
  content     LONGTEXT    NOT NULL,
  cover       VARCHAR(255) DEFAULT NULL,
  author      VARCHAR(64) DEFAULT 'PetMate 兽医团队',
  view_count  INT         NOT NULL DEFAULT 0,
  seo_url     VARCHAR(255) DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted  TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文章';

DROP TABLE IF EXISTS t_article_category;
CREATE TABLE t_article_category (
  id    BIGINT      NOT NULL AUTO_INCREMENT,
  name  VARCHAR(32) NOT NULL,
  icon  VARCHAR(255) DEFAULT NULL,
  sort  INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类';

-- ============================================================
-- 风控 / 后台
-- ============================================================
DROP TABLE IF EXISTS t_sensitive_word;
CREATE TABLE t_sensitive_word (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  word        VARCHAR(64) NOT NULL,
  category    VARCHAR(16) NOT NULL DEFAULT '其他' COMMENT '分类: 政治/暴力/色情/广告/侮辱/其他',
  level       TINYINT     NOT NULL DEFAULT 1 COMMENT '级别 1低 2中 3高',
  action      VARCHAR(8)  NOT NULL DEFAULT 'block' COMMENT '命中处置: block(拦截)/mask(替换*)/review(进审核)',
  enabled     TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
  hit_count   INT         NOT NULL DEFAULT 0 COMMENT '累计命中次数',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词';

DROP TABLE IF EXISTS t_operation_log;
CREATE TABLE t_operation_log (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  merchant_id   BIGINT      NOT NULL,
  operator_id   BIGINT      NOT NULL,
  operator_name VARCHAR(64) DEFAULT NULL,
  role          VARCHAR(32) DEFAULT NULL,
  module        VARCHAR(32) NOT NULL,
  action        VARCHAR(64) NOT NULL,
  target        TEXT        DEFAULT NULL,
  detail        VARCHAR(512) DEFAULT NULL,
  ip            VARCHAR(64) DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_merchant (merchant_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作日志';

DROP TABLE IF EXISTS t_system_config;
CREATE TABLE t_system_config (
  config_key   VARCHAR(64) NOT NULL,
  config_value TEXT        NOT NULL,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

DROP TABLE IF EXISTS t_follow;
CREATE TABLE t_follow (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  follower_id  BIGINT   NOT NULL,
  followee_id  BIGINT   NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow (follower_id, followee_id),
  KEY idx_follower (follower_id),
  KEY idx_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系';

DROP TABLE IF EXISTS t_report;
CREATE TABLE t_report (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  reporter_id BIGINT      NOT NULL,
  target_type VARCHAR(16) NOT NULL COMMENT 'post/comment/user',
  target_id   BIGINT      NOT NULL,
  reason      VARCHAR(255) DEFAULT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/resolved/rejected',
  handle_note VARCHAR(255) DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at  DATETIME    DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报';

-- ============================================================
-- 营销域：秒杀 / 领养（增量，见 sql/add_seckill_adoption.sql）
-- ============================================================

CREATE TABLE IF NOT EXISTS t_seckill (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  product_id    BIGINT      NOT NULL,
  title         VARCHAR(255) NOT NULL,
  seckill_price DECIMAL(10,2) NOT NULL,
  stock         INT         NOT NULL DEFAULT 0,
  sold          INT         NOT NULL DEFAULT 0,
  start_time    DATETIME    NOT NULL,
  end_time      DATETIME    NOT NULL,
  status        TINYINT     NOT NULL DEFAULT 1 COMMENT '0下架/1上架',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_time (start_time, end_time),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS t_adoption (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  title         VARCHAR(255) NOT NULL,
  pet_type      VARCHAR(32)  NOT NULL COMMENT 'cat/dog/other',
  breed         VARCHAR(64)  DEFAULT NULL,
  gender        TINYINT     NOT NULL DEFAULT 0 COMMENT '0未知/1男/2女',
  age           VARCHAR(32)  DEFAULT NULL,
  rescue_story  TEXT         DEFAULT NULL,
  health_status VARCHAR(255) DEFAULT NULL,
  vaccinated    TINYINT     NOT NULL DEFAULT 0,
  neutered      TINYINT     NOT NULL DEFAULT 0,
  city          VARCHAR(64)  DEFAULT NULL,
  contact       VARCHAR(64)  DEFAULT NULL,
  images        JSON         DEFAULT NULL,
  status        TINYINT     NOT NULL DEFAULT 1 COMMENT '0下架/1待领养',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待领养宠物';

CREATE TABLE IF NOT EXISTS t_adoption_application (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  adoption_id   BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  name          VARCHAR(64) NOT NULL,
  phone         VARCHAR(20) NOT NULL,
  reason        VARCHAR(255) DEFAULT NULL,
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审核/1通过/2驳回',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_adoption (adoption_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领养申请';

-- 轮播 Banner 配置
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

-- 平台公告
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
