-- 新增「秒杀」与「领养」功能所需表（增量迁移，不影响现有表）
-- 运行：mysql -h localhost -u root -p123456 petmate --default-character-set=utf8mb4 < sql/add_seckill_adoption.sql

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

-- ============ 种子数据（幂等：已存在则跳过）============
INSERT INTO t_seckill (id, product_id, title, seckill_price, stock, sold, start_time, end_time, status)
SELECT 1, 1001, '皇家猫粮 K36 成猫专用 4kg 限时秒杀', 99.00, 50, 0, '2026-07-20 00:00:00', '2026-07-29 23:59:59', 1
WHERE NOT EXISTS (SELECT 1 FROM t_seckill WHERE id = 1);

INSERT INTO t_seckill (id, product_id, title, seckill_price, stock, sold, start_time, end_time, status)
SELECT 2, 1003, '小佩 SOLO 智能宠物饮水机 闪购', 299.00, 30, 0, '2026-07-20 00:00:00', '2026-07-29 23:59:59', 1
WHERE NOT EXISTS (SELECT 1 FROM t_seckill WHERE id = 2);

INSERT INTO t_seckill (id, product_id, title, seckill_price, stock, sold, start_time, end_time, status)
SELECT 3, 1006, 'ZEZE 宠物互动玩具组合 秒杀', 29.00, 100, 0, '2026-07-20 00:00:00', '2026-07-29 23:59:59', 1
WHERE NOT EXISTS (SELECT 1 FROM t_seckill WHERE id = 3);

INSERT INTO t_adoption (id, title, pet_type, breed, gender, age, rescue_story, health_status, vaccinated, neutered, city, contact, images, status)
SELECT 1, '橘猫「豆豆」寻找温暖的家', 'cat', '中华田园橘猫', 2, '约 1 岁', '街头救助，性格温顺亲人，已驱虫。', '健康，已绝育', 1, 1, '北京', '138****0001', JSON_ARRAY('/src/assets/images/cart/p1-dogfood.jpg'), 1
WHERE NOT EXISTS (SELECT 1 FROM t_adoption WHERE id = 1);

INSERT INTO t_adoption (id, title, pet_type, breed, gender, age, rescue_story, health_status, vaccinated, neutered, city, contact, images, status)
SELECT 2, '柯基「布丁」待领养', 'dog', '柯基', 1, '约 2 岁', '原主人搬迁无法继续饲养，寻找有经验的家庭。', '体检正常', 1, 0, '上海', '139****0002', JSON_ARRAY('/src/assets/images/cart/p2-salmon.jpg'), 1
WHERE NOT EXISTS (SELECT 1 FROM t_adoption WHERE id = 2);

INSERT INTO t_adoption (id, title, pet_type, breed, gender, age, rescue_story, health_status, vaccinated, neutered, city, contact, images, status)
SELECT 3, '流浪狗「团子」寻找领养人', 'dog', '串串', 1, '约 3 岁', '收容所救助，亲人友善，适合有庭院家庭。', '健康', 0, 1, '广州', '137****0003', JSON_ARRAY('/src/assets/images/cart/p3-fountain.jpg'), 1
WHERE NOT EXISTS (SELECT 1 FROM t_adoption WHERE id = 3);
