-- 升级 t_sensitive_word：从「平铺字符串列表」升级为「可分类/分级/可配处置/可统计」的敏感词表
-- 适用于已存在旧表（仅 id/word/created_at）的存量库；新建库以 schema.sql 为准。

ALTER TABLE t_sensitive_word
  ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT '其他' COMMENT '分类: 政治/暴力/色情/广告/侮辱/其他',
  ADD COLUMN level    TINYINT      NOT NULL DEFAULT 1 COMMENT '级别 1低 2中 3高',
  ADD COLUMN action   VARCHAR(8)   NOT NULL DEFAULT 'block' COMMENT '命中处置: block(拦截)/mask(替换*)/review(进审核)',
  ADD COLUMN enabled  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
  ADD COLUMN hit_count INT          NOT NULL DEFAULT 0 COMMENT '累计命中次数';

-- 给存量种子词补一个更合理的分类（不影响功能，仅便于演示）
UPDATE t_sensitive_word SET category = '广告' WHERE word = '垃圾广告';
UPDATE t_sensitive_word SET category = '暴力' WHERE word = '暴力';
UPDATE t_sensitive_word SET level = 2 WHERE word = '垃圾广告';
UPDATE t_sensitive_word SET level = 3 WHERE word = '暴力';
UPDATE t_sensitive_word SET action = 'review' WHERE word = '敏感';
