package com.petmate.admin;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 管理后台轮播 Banner 配置：列表 / 新建 / 编辑 / 删除 / 上下线。
 */
@RestController
public class BannerController {

    private final JdbcTemplate jdbc;
    public BannerController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private void adminOnly() { TokenContext.requireRoleAndActive("admin"); }

    /** 列表（按 sort 升序，其次新建的靠前） */
    @GetMapping("/admin/banner/list")
    public ApiResp<List<Map<String, Object>>> list() {
        adminOnly();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, title, image_url, link_url, sort, status, start_time, end_time, created_at, updated_at " +
            "FROM t_banner WHERE is_deleted = 0 ORDER BY sort ASC, id DESC");
        return ApiResp.ok(rows);
    }

    /** 新建 */
    @PostMapping("/admin/banner")
    public ApiResp<Void> create(@RequestBody Map<String, Object> body) {
        adminOnly();
        String title = str(body, "title");
        if (title == null || title.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "标题必填");
        jdbc.update(
            "INSERT INTO t_banner (title, image_url, link_url, sort, status, start_time, end_time, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,?,0,NOW(),NOW())",
            title, str(body, "image_url"), str(body, "link_url"),
            num(body, "sort", 0), num(body, "status", 1),
            str(body, "startTime"), str(body, "endTime"));
        return ApiResp.ok();
    }

    /** 编辑（动态字段） */
    @PutMapping("/admin/banner/{id}")
    public ApiResp<Void> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE t_banner SET updated_at = NOW()");
        if (body.containsKey("title")) { sql.append(", title = ?"); args.add(str(body, "title")); }
        if (body.containsKey("image_url")) { sql.append(", image_url = ?"); args.add(str(body, "image_url")); }
        if (body.containsKey("link_url")) { sql.append(", link_url = ?"); args.add(str(body, "link_url")); }
        if (body.containsKey("sort")) { sql.append(", sort = ?"); args.add(num(body, "sort", 0)); }
        if (body.containsKey("status")) { sql.append(", status = ?"); args.add(num(body, "status", 1)); }
        if (body.containsKey("startTime")) { sql.append(", start_time = ?"); args.add(str(body, "startTime")); }
        if (body.containsKey("endTime")) { sql.append(", end_time = ?"); args.add(str(body, "endTime")); }
        sql.append(" WHERE id = ? AND is_deleted = 0");
        args.add(id);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    /** 上下线切换 */
    @PutMapping("/admin/banner/{id}/status")
    public ApiResp<Void> toggleStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        int status = num(body, "status", 1);
        jdbc.update("UPDATE t_banner SET status = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0", status, id);
        return ApiResp.ok();
    }

    /** 删除（软删） */
    @DeleteMapping("/admin/banner/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        adminOnly();
        jdbc.update("UPDATE t_banner SET is_deleted = 1, updated_at = NOW() WHERE id = ?", id);
        return ApiResp.ok();
    }

    private String str(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
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
