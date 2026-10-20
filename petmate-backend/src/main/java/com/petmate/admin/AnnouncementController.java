package com.petmate.admin;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 管理后台平台公告：列表 / 新建 / 编辑 / 删除 / 推送。
 * 推送复用 /admin/notify 的"纯推送"语义（记录到 t_operation_log），不真正投递。
 */
@RestController
public class AnnouncementController {

    private final JdbcTemplate jdbc;
    public AnnouncementController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void adminOnly() { TokenContext.requireRoleAndActive("admin"); }

    /** 列表（分页） */
    @GetMapping("/admin/announcement/list")
    public ApiResp<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminOnly();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, title, content, cover, target, status, sort, created_at, updated_at, published_at " +
            "FROM t_announcement WHERE is_deleted = 0 ORDER BY sort ASC, id DESC LIMIT ? OFFSET ?",
            size, (page - 1) * size);
        return ApiResp.ok(rows);
    }

    /** 新建（status=1 视为发布，记录 published_at） */
    @PostMapping("/admin/announcement")
    public ApiResp<Void> create(@RequestBody Map<String, Object> body) {
        adminOnly();
        String title = str(body, "title");
        if (title == null || title.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "标题必填");
        int status = num(body, "status", 0);
        Object publishedAt = (status == 1) ? new java.sql.Timestamp(System.currentTimeMillis()) : null;
        jdbc.update(
            "INSERT INTO t_announcement (title, content, cover, target, status, sort, is_deleted, published_at, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,?,NOW(),NOW())",
            title, str(body, "content"), str(body, "cover"),
            str(body, "target", "all"), status, num(body, "sort", 0), publishedAt);
        return ApiResp.ok();
    }

    /** 编辑（动态字段） */
    @PutMapping("/admin/announcement/{id}")
    public ApiResp<Void> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE t_announcement SET updated_at = NOW()");
        if (body.containsKey("title")) { sql.append(", title = ?"); args.add(str(body, "title")); }
        if (body.containsKey("content")) { sql.append(", content = ?"); args.add(str(body, "content")); }
        if (body.containsKey("cover")) { sql.append(", cover = ?"); args.add(str(body, "cover")); }
        if (body.containsKey("target")) { sql.append(", target = ?"); args.add(str(body, "target")); }
        if (body.containsKey("sort")) { sql.append(", sort = ?"); args.add(num(body, "sort", 0)); }
        if (body.containsKey("status")) { sql.append(", status = ?"); args.add(num(body, "status", 0)); }
        sql.append(" WHERE id = ? AND is_deleted = 0");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    /** 删除（软删） */
    @DeleteMapping("/admin/announcement/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        adminOnly();
        jdbc.update("UPDATE t_announcement SET is_deleted = 1, updated_at = NOW() WHERE id = ?", id);
        return ApiResp.ok();
    }

    /** 推送：复用 /admin/notify 语义（写 t_operation_log），并把公告标记为已发布 */
    @PostMapping("/admin/announcement/{id}/push")
    public ApiResp<Void> push(@PathVariable long id) {
        adminOnly();
        Map<String, Object> a = jdbc.queryForMap(
            "SELECT title, content, target FROM t_announcement WHERE id = ? AND is_deleted = 0", id);
        String title = String.valueOf(a.get("title"));
        String content = a.get("content") == null ? "" : String.valueOf(a.get("content"));
        String target = a.get("target") == null ? "all" : String.valueOf(a.get("target"));
        jdbc.update("UPDATE t_announcement SET status = 1, published_at = NOW(), updated_at = NOW() WHERE id = ?", id);
        jdbc.update(
            "INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
            "VALUES (0, ?, NULL, ?, 'notify', 'PUSH_NOTIFY', ?, ?)",
            TokenContext.get().userId, TokenContext.get().role, target, title + "|" + content);
        return ApiResp.ok();
    }

    private String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
    private String str(Map<String, Object> b, String k, String d) {
        String s = str(b, k);
        return s == null ? d : s;
    }
    private int num(Map<String, Object> b, String k, int d) {
        Object v = b.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (Exception e) { return d; }
        }
        return d;
    }
}
