-- ============================================================
-- v4.0: 商家申请入驻
--   - 新增 t_merchant_apply 表(待审/通过/驳回 三态)
--   - 一个手机号只允许一份申请(uk_phone)
--   - 审核通过时生成 t_user(merchant 区间) + t_store(audit_status=1)
-- ============================================================

DROP TABLE IF EXISTS t_merchant_apply;
CREATE TABLE t_merchant_apply (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  phone           VARCHAR(20)  NOT NULL COMMENT '申请人手机号(登录账号)',
  password_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt(申请时填的密码),审核通过后写入 t_user.password',
  owner_name      VARCHAR(32)  NOT NULL COMMENT '店主姓名',
  store_name      VARCHAR(64)  NOT NULL COMMENT '门店名称',
  type            VARCHAR(32)  NOT NULL COMMENT '门店类型: grooming/medical/boarding/comprehensive',
  address         VARCHAR(255) NOT NULL COMMENT '详细地址',
  lng             DECIMAL(10,6) DEFAULT NULL,
  lat             DECIMAL(10,6) DEFAULT NULL,
  business_hours  VARCHAR(255) DEFAULT NULL COMMENT '营业时间文本',
  photos          JSON          DEFAULT NULL COMMENT '门店照片 URL 数组',
  license_no      VARCHAR(64)   DEFAULT NULL COMMENT '营业执照号',
  remark          VARCHAR(255)  DEFAULT NULL COMMENT '申请人备注',
  status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待审核 / 1 通过 / 2 驳回',
  submit_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at     DATETIME      DEFAULT NULL,
  review_note     VARCHAR(255)  DEFAULT NULL,
  reviewer_id     BIGINT        DEFAULT NULL COMMENT '审核人 t_user.id',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家入驻申请';