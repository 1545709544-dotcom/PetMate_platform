-- ============================================================
-- 回滚：对 t_store.id=5003 执行的 force-remove 测试
-- 生成时间：2026-07-29
-- 操作人：联调测试
-- ============================================================
-- 变更点：
--   1) t_store.status 0 -> 2
--   2) t_user(merchant_id=20002).status 已为 1（测试前就是封禁态,本次没动）
--   3) t_product / t_service_item 本就 0 条,本次没影响
--
-- 回滚 SQL（按需执行）：

-- (1) t_store 状态还原
UPDATE t_store SET status = 0, is_deleted = 0 WHERE id = 5003;

-- (2) 如果想恢复店主账号
--    t_user(20002).status 测试前就是 1（封禁态）,force-remove 没改它
--    想要恢复正常就执行:
-- UPDATE t_user SET status = 0 WHERE id = 20002;

-- (3) 操作日志清理（可选,删除本次测试写入的日志）
-- DELETE FROM t_operation_log
-- WHERE module = 'merchant' AND action = 'MERCHANT_FORCE_REMOVE'
--   AND target = '5003' AND operator_id = 90001;
