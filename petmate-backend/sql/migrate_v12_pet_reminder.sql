-- ============================================================
-- migrate_v12_pet_reminder.sql
-- 宠物关怀提醒引擎：疫苗/驱虫/体检/自定义 的自动到期提醒
-- 依赖：t_pet、t_pet_health_record、t_user_notification 均已存在
-- 设计参考市面宠物 App（PetPals / 宠搭搭 / Pet Line / 宠星）：
--   每只宠物独立健康档案 -> 记录带下次到期日 -> 提前 N 天自动推送提醒
-- ============================================================

CREATE TABLE IF NOT EXISTS t_pet_reminder (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pet_id        BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  type          VARCHAR(16)  NOT NULL DEFAULT 'custom',   -- vaccine / deworming / checkup / custom
  title         VARCHAR(64)  NOT NULL,
  due_date      DATE         NOT NULL,                   -- 到期日（即下一次应处理日期）
  advance_days  INT          NOT NULL DEFAULT 3,          -- 提前几天提醒（市面普遍 3 天）
  cycle_days    INT          NOT NULL DEFAULT 0,         -- 循环周期（天），0 = 一次性
  status        VARCHAR(16)  NOT NULL DEFAULT 'pending', -- pending / overdue / done
  last_reminded_at DATETIME  DEFAULT NULL,               -- 防重：上次推送时间
  done_at       DATETIME     DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted    TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_user (user_id, is_deleted),
  INDEX idx_pet (pet_id, is_deleted),
  INDEX idx_due (due_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 从已有健康记录（reminder=1 且有下次日期）生成提醒，避免重复
INSERT INTO t_pet_reminder (pet_id, user_id, type, title, due_date, advance_days, cycle_days, status)
SELECT hr.pet_id, p.user_id,
       CASE hr.record_type
            WHEN 'vaccine'   THEN 'vaccine'
            WHEN 'deworming' THEN 'deworming'
            WHEN 'checkup'   THEN 'checkup'
            ELSE 'custom'
       END,
       hr.title, hr.next_date, 3, 0, 'pending'
FROM t_pet_health_record hr
JOIN t_pet p ON p.id = hr.pet_id
WHERE hr.is_deleted = 0
  AND hr.reminder = 1
  AND hr.next_date IS NOT NULL
  AND p.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM t_pet_reminder r
      WHERE r.pet_id = hr.pet_id
        AND r.title = hr.title
        AND r.due_date = hr.next_date
        AND r.is_deleted = 0
  );
