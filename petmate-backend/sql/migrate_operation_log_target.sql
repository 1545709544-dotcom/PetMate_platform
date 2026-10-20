-- 修复：t_operation_log.target 长度不足导致批量新增敏感词时写入截断报错
-- 原 live 库为 varchar(128)，批量新增时把多个词用逗号拼进 target 易超长。
-- 改为 TEXT，彻底解除长度限制（该列仅作审计展示，无索引依赖）。
ALTER TABLE t_operation_log MODIFY COLUMN target TEXT NULL COMMENT '操作目标（敏感词批量新增时为词列表）';
