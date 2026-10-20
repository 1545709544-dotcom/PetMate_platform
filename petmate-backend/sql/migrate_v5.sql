-- ============================================================
-- v5.0: 商家入驻申请 - 扩展资质字段
--   - 新增主体类型 / 营业执照 / 法人身份证 / 企业信息等字段
--   - 与前端 4 步入驻向导(资质→门店→签约→完成)对齐
-- ============================================================

ALTER TABLE t_merchant_apply
  ADD COLUMN subject_type        VARCHAR(16)  NOT NULL DEFAULT 'individual' COMMENT '主体类型: individual 个体商户 / enterprise 企业商户',
  ADD COLUMN business_license_url VARCHAR(512) DEFAULT NULL COMMENT '营业执照图片 URL(企业商户必传)',
  ADD COLUMN id_card_front_url    VARCHAR(512) DEFAULT NULL COMMENT '法人身份证正面图片 URL',
  ADD COLUMN id_card_back_url     VARCHAR(512) DEFAULT NULL COMMENT '法人身份证反面图片 URL',
  ADD COLUMN enterprise_name      VARCHAR(128) DEFAULT NULL COMMENT '企业名称(仅企业商户)',
  ADD COLUMN unified_credit_code VARCHAR(32)  DEFAULT NULL COMMENT '统一社会信用代码(仅企业商户)',
  ADD COLUMN store_phone         VARCHAR(20)  DEFAULT NULL COMMENT '门店联系电话(与登录手机号可不同)';
