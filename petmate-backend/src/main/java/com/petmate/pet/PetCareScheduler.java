package com.petmate.pet;

import com.petmate.common.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 宠物关怀提醒调度器（"疫苗到期自动提醒"引擎核心）。
 *
 * 依赖：
 *   - t_pet_reminder（migrate_v12 加表）
 *   - NotifyService.notifyUser -> t_user_notification（驱动用户端消息铃铛）
 *   - PetMateApplication 已 @EnableScheduling
 *
 * 策略（参照市面宠物 App 的"提前 3 天推送"）：
 *   1) 把 status=pending 且 due_date < 今天 的记录标记为 overdue（逾期）。
 *   2) 对 status IN (pending, overdue) 且进入"提前窗口"（due_date <= 今天+advance_days）
 *      且尚未推送过（last_reminded_at IS NULL）的记录，推送一条站内信关怀提醒，
 *      并写入 last_reminded_at 防止重复打扰。
 *   幂等：靠 last_reminded_at 去重，多次扫描不会重复发信。
 */
@Component
public class PetCareScheduler {

    private static final Logger log = LoggerFactory.getLogger(PetCareScheduler.class);

    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;

    public PetCareScheduler(JdbcTemplate jdbc, NotifyService notifyService) {
        this.jdbc = jdbc;
        this.notifyService = notifyService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void sweep() {
        try {
            LocalDate today = LocalDate.now();

            // 1) 逾期标记
            int overdue = jdbc.update(
                "UPDATE t_pet_reminder SET status = 'overdue' " +
                "WHERE status = 'pending' AND due_date < ? AND is_deleted = 0",
                Date.valueOf(today));
            if (overdue > 0) log.info("[pet-care] marked {} reminder(s) overdue", overdue);

            // 2) 提前 / 逾期提醒（窗口内且未推送过）
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id, r.user_id, r.pet_id, r.type, r.title, r.due_date, p.name AS pet_name " +
                "FROM t_pet_reminder r JOIN t_pet p ON p.id = r.pet_id " +
                "WHERE r.status IN ('pending', 'overdue') AND r.is_deleted = 0 AND p.is_deleted = 0 " +
                "  AND r.due_date <= DATE_ADD(?, INTERVAL r.advance_days DAY) " +
                "  AND r.last_reminded_at IS NULL",
                Date.valueOf(today));

            for (Map<String, Object> r : rows) {
                long userId = ((Number) r.get("user_id")).longValue();
                String petName = (String) r.get("pet_name");
                String title = (String) r.get("title");
                LocalDate due = ((Date) r.get("due_date")).toLocalDate();
                String dueStr = due.toString();
                String content;
                if (due.isBefore(today)) {
                    content = String.format("您家「%s」的「%s」已逾期（应于 %s 完成），请尽快安排。", petName, title, dueStr);
                } else if (due.isEqual(today)) {
                    content = String.format("提醒：您家「%s」的「%s」今天到期，请及时处理。", petName, title);
                } else {
                    content = String.format("提醒：您家「%s」的「%s」将于 %s 到期，请提前安排。", petName, title, dueStr);
                }
                notifyService.notifyUser(userId, "care", "宠物关怀提醒", content, "pet:" + r.get("pet_id"));
                jdbc.update("UPDATE t_pet_reminder SET last_reminded_at = NOW() WHERE id = ?",
                    ((Number) r.get("id")).longValue());
            }
            if (!rows.isEmpty()) log.info("[pet-care] sent {} care reminder(s)", rows.size());
        } catch (Exception e) {
            log.warn("[pet-care] sweep error", e);
        }
    }
}
