-- ============================================================
-- v6.0: 商家入驻申请 - 增加法人身份证号文本字段
--   - 原 id_card_front_url / id_card_back_url 仅存图片 URL
--   - 新增 id_card 存 18 位居民身份证号,用于身份核验
-- ============================================================

ALTER TABLE t_merchant_apply
  ADD COLUMN id_card VARCHAR(18) DEFAULT NULL COMMENT '法人/店主身份证号(15或18位)';
