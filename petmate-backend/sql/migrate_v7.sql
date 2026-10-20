-- ============================================================
-- v7.0: 商家管理 - t_store 加 status 字段
--   - 用于后台「商家管理」页：封禁/解封/强制解除
--   - 0=营业中(默认,与 audit_status=1 配合)  1=已封禁  2=已注销
--   - 与 audit_status 的区别:
--       audit_status  = 平台审核(待审/通过/驳回)
--       status        = 运营状态(营业/封禁/注销)
-- ============================================================

ALTER TABLE t_store
  ADD COLUMN status TINYINT NOT NULL DEFAULT 0
    COMMENT '运营状态 0=营业中 1=已封禁 2=已注销'
    AFTER audit_status;

-- 索引:后台按 status 过滤已封禁/已注销商家
ALTER TABLE t_store
  ADD INDEX idx_status (status);
