package com.petmate.pet;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.NotifyService;
import com.petmate.security.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * 宠物关怀提醒接口：疫苗 / 驱虫 / 体检 / 自定义 的到期提醒。
 *
 * 与 t_pet_health_record 的关系：
 *   - health_record = 健康"日志"（某次接种/驱虫的记录）
 *   - reminder       = 待办"任务"（下一次何时该做 + 是否提醒）
 * 录入健康记录(reminder=1 且带 nextDate)会同步生成一条 reminder，
 * 也可单独手动创建。任务到期由 PetCareScheduler 自动推送站内信。
 */
@RestController
@RequestMapping("/pet")
public class CareReminderController {

    private static final Logger log = LoggerFactory.getLogger(CareReminderController.class);
    private static final Set<String> TYPES = Set.of("vaccine", "deworming", "checkup", "custom");

    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;

    public CareReminderController(JdbcTemplate jdbc, NotifyService notifyService) {
        this.jdbc = jdbc;
        this.notifyService = notifyService;
    }

    /** 当前用户全部宠物的提醒聚合（前端"今日待办"数据源） */
    @GetMapping("/user/reminders")
    public ApiResp<Map<String, Object>> userReminders() {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.pet_id, r.type, r.title, r.due_date, r.advance_days, r.cycle_days, r.status, " +
            "p.name AS pet_name " +
            "FROM t_pet_reminder r JOIN t_pet p ON p.id = r.pet_id " +
            "WHERE r.user_id = ? AND r.is_deleted = 0 AND p.is_deleted = 0 " +
            "ORDER BY CASE r.status WHEN 'overdue' THEN 0 WHEN 'pending' THEN 1 ELSE 2 END, r.due_date ASC",
            uid);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> overdue = new ArrayList<>();
        List<Map<String, Object>> dueSoon = new ArrayList<>();
        List<Map<String, Object>> done = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = normalize(r);
            String status = (String) r.get("status");
            LocalDate due = ((Date) r.get("due_date")).toLocalDate();
            if ("done".equals(status)) {
                done.add(m);
            } else if ("overdue".equals(status) || due.isBefore(today)) {
                overdue.add(m);
            } else {
                dueSoon.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overdue", overdue);
        out.put("dueSoon", dueSoon);
        out.put("done", done);
        out.put("counts", Map.of(
            "overdue", overdue.size(),
            "dueSoon", dueSoon.size(),
            "done", done.size(),
            "pending", overdue.size() + dueSoon.size()));
        return ApiResp.ok(out);
    }

    /** 单只宠物的提醒列表 */
    @GetMapping("/{id}/reminders")
    public ApiResp<List<Map<String, Object>>> petReminders(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        assertOwnership(id, uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, pet_id, type, title, due_date, advance_days, cycle_days, status, done_at, created_at " +
            "FROM t_pet_reminder WHERE pet_id = ? AND is_deleted = 0 ORDER BY " +
            "CASE status WHEN 'overdue' THEN 0 WHEN 'pending' THEN 1 ELSE 2 END, due_date ASC", id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(normalize(r));
        return ApiResp.ok(out);
    }

    /** 手动创建提醒（支持循环周期 cycleDays，0=一次性） */
    @PostMapping("/reminder")
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        long petId = ((Number) body.get("petId")).longValue();
        assertOwnership(petId, uid);

        String title = body.get("title") == null ? null : ((String) body.get("title")).trim();
        if (title == null || title.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "title 必填");
        if (title.length() > 64) throw new BizException(ErrorCode.PARAM_ERROR, "title 不超过 64 字符");

        String type = body.get("type") == null ? "custom" : ((String) body.get("type")).trim();
        if (type.isEmpty()) type = "custom";
        if (!TYPES.contains(type)) throw new BizException(ErrorCode.PARAM_ERROR, "type 必须是 vaccine/deworming/checkup/custom 之一");

        String dueDate = body.get("dueDate") == null ? null : ((String) body.get("dueDate")).trim();
        if (dueDate == null || dueDate.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "dueDate 必填");
        LocalDate due;
        try {
            due = LocalDate.parse(dueDate);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "dueDate 格式需为 yyyy-MM-dd");
        }

        int advanceDays = optInt(body.get("advanceDays"), 3);
        if (advanceDays < 0) advanceDays = 0;
        if (advanceDays > 30) advanceDays = 30;
        int cycleDays = optInt(body.get("cycleDays"), 0);
        if (cycleDays < 0) cycleDays = 0;

        jdbc.update(
            "INSERT INTO t_pet_reminder (pet_id, user_id, type, title, due_date, advance_days, cycle_days, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
            petId, uid, type, title, Date.valueOf(due), advanceDays, cycleDays);
        long rid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return ApiResp.ok(Map.of("id", rid));
    }

    /** 标记完成；若设置了循环周期，自动生成下一次提醒 */
    @PostMapping("/reminder/{id}/done")
    public ApiResp<Map<String, Object>> done(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = assertReminderOwnership(id, uid);
        LocalDate doneDate = LocalDate.now();
        jdbc.update("UPDATE t_pet_reminder SET status = 'done', done_at = ? WHERE id = ?", Date.valueOf(doneDate), id);

        int cycleDays = ((Number) r.get("cycle_days")).intValue();
        long petId = ((Number) r.get("pet_id")).longValue();
        String type = (String) r.get("type");
        String title = (String) r.get("title");
        int advanceDays = ((Number) r.get("advance_days")).intValue();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("cycled", cycleDays > 0);
        if (cycleDays > 0) {
            LocalDate next = doneDate.plusDays(cycleDays);
            jdbc.update(
                "INSERT INTO t_pet_reminder (pet_id, user_id, type, title, due_date, advance_days, cycle_days, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
                petId, uid, type, title, Date.valueOf(next), advanceDays, cycleDays);
            long nid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            out.put("nextReminderId", nid);
            out.put("nextDueDate", next.toString());
        }
        return ApiResp.ok(out);
    }

    /** 删除提醒 */
    @DeleteMapping("/reminder/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        assertReminderOwnership(id, uid);
        jdbc.update("UPDATE t_pet_reminder SET is_deleted = 1 WHERE id = ?", id);
        return ApiResp.ok();
    }

    // ================ 内部工具 ================

    private int optInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private Map<String, Object> assertReminderOwnership(long id, long uid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.pet_id, r.type, r.title, r.advance_days, r.cycle_days " +
            "FROM t_pet_reminder r WHERE r.id = ? AND r.user_id = ? AND r.is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "提醒不存在");
        return rows.get(0);
    }

    private void assertOwnership(long petId, long uid) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_pet WHERE id = ? AND user_id = ? AND is_deleted = 0",
            Integer.class, petId, uid);
        if (cnt == null || cnt == 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "宠物不存在或已删除");
    }

    private Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("petId", r.get("pet_id"));
        m.put("type", r.get("type"));
        m.put("title", r.get("title"));
        m.put("dueDate", r.get("due_date").toString());
        m.put("advanceDays", ((Number) r.get("advance_days")).intValue());
        m.put("cycleDays", ((Number) r.get("cycle_days")).intValue());
        m.put("status", r.get("status"));
        if (r.get("pet_name") != null) m.put("petName", r.get("pet_name"));
        if (r.get("done_at") != null) m.put("doneAt", r.get("done_at").toString());
        if (r.get("created_at") != null) m.put("createdAt", r.get("created_at").toString());
        return m;
    }
}
