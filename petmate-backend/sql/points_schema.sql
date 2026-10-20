-- ============================================================
-- 鱼干（积分）体系 schema
-- 导入： mysql --default-character-set=utf8mb4 -u root -p123456 petmate < points_schema.sql
-- 可重复执行（DROP IF EXISTS + INSERT IGNORE）
-- ============================================================

SET NAMES utf8mb4;

-- 余额账户（每用户一行）
DROP TABLE IF EXISTS t_points_account;
CREATE TABLE t_points_account (
    user_id       BIGINT       NOT NULL,
    balance       INT          NOT NULL DEFAULT 0  COMMENT '当前鱼干余额',
    total_earned  INT          NOT NULL DEFAULT 0  COMMENT '累计获得',
    total_spent   INT          NOT NULL DEFAULT 0  COMMENT '累计消耗',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鱼干余额账户';

-- 流水（每次变动一条）
DROP TABLE IF EXISTS t_points_log;
CREATE TABLE t_points_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    change_type  VARCHAR(20)  NOT NULL COMMENT 'signin/post/task/consume/redeem/admin',
    change_value INT          NOT NULL COMMENT '正=获得 负=消耗',
    balance_after INT         NOT NULL DEFAULT 0,
    remark       VARCHAR(255) DEFAULT NULL,
    ref_id       BIGINT       DEFAULT NULL COMMENT '关联业务ID(订单/动态/任务)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_date (user_id, created_at),
    KEY idx_user_type (user_id, change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鱼干流水';

-- 任务定义
DROP TABLE IF EXISTS t_task_def;
CREATE TABLE t_task_def (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL COMMENT '唯一任务码',
    title       VARCHAR(80)  NOT NULL,
    description VARCHAR(200) DEFAULT NULL,
    reward      INT          NOT NULL DEFAULT 0 COMMENT '奖励鱼干',
    daily       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=每日可完成 0=仅一次',
    sort        INT          NOT NULL DEFAULT 0,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务定义';

-- 任务完成记录
DROP TABLE IF EXISTS t_task_record;
CREATE TABLE t_task_record (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    task_code  VARCHAR(40)  NOT NULL,
    ref_id     BIGINT       DEFAULT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_task (user_id, task_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务完成记录';

-- 订单增加鱼干抵扣字段
ALTER TABLE t_order
    ADD COLUMN points_used   INT          NOT NULL DEFAULT 0 COMMENT '使用的鱼干数' AFTER coupon_amount,
    ADD COLUMN points_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '鱼干抵扣金额(¥)' AFTER points_used;

-- ===================== 任务种子 =====================
-- 每日签到(+5) / 完善资料(+20) / 首次发动态(+10) / 分享(+5)
INSERT IGNORE INTO t_task_def (id, code, title, description, reward, daily, sort, enabled) VALUES
(1, 'signin',           '每日签到',     '每天登录签到即可领取鱼干', 5,  1, 1, 1),
(2, 'complete_profile', '完善个人资料', '补全昵称/头像/简介等资料', 20, 0, 2, 1),
(3, 'first_post',       '首次发布动态', '发布你的第一条宠物动态',   10, 0, 3, 1),
(4, 'share',            '分享给好友',   '把 PetMate 分享给朋友',    5,  1, 4, 1);
