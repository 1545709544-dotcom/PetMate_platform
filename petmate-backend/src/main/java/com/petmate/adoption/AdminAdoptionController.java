package com.petmate.adoption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/admin/adoption")
public class AdminAdoptionController {

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAdoptionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void adminOnly() {
        TokenContext.requireRoleAndActive("admin");
    }

    // ============ 列表（全部，含下架，供管理后台）============
    @GetMapping("")
    public ApiResp<List<Map<String, Object>>> list(
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        adminOnly();
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, pet_type, breed, gender, age, city, status, created_at " +
                "FROM t_adoption WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (petType != null && !petType.isBlank()) {
            sql.append(" AND pet_type = ?");
            args.add(petType);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND title LIKE ?");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY id DESC");
        return ApiResp.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // ============ 创建（发布领养信息）============
    @PostMapping("")
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        adminOnly();
        String title = str(b.get("title"));
        if (title == null || title.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写宠物名称/标题");
        String petType = str(b.get("petType"));
        if (petType == null || petType.isBlank()) petType = "other";

        int gender = num(b.get("gender"), 0);
        int vaccinated = bool(b.get("vaccinated")) ? 1 : 0;
        int neutered = bool(b.get("neutered")) ? 1 : 0;
        int status = num(b.get("status"), 1);
        String imagesJson = toImagesJson(b.get("images"));

        jdbc.update(
                "INSERT INTO t_adoption (title, pet_type, breed, gender, age, rescue_story, health_status, " +
                "vaccinated, neutered, city, contact, images, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                title,
                petType,
                str(b.get("breed")),
                gender,
                str(b.get("age")),
                str(b.get("rescueStory")),
                str(b.get("healthStatus")),
                vaccinated,
                neutered,
                str(b.get("city")),
                str(b.get("contact")),
                imagesJson,
                status);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    // ============ 更新 ============
    @PutMapping("/{id}")
    public ApiResp<Void> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        adminOnly();
        List<Map<String, Object>> exist = jdbc.queryForList(
                "SELECT id FROM t_adoption WHERE id = ? AND is_deleted = 0", id);
        if (exist.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在");

        String imagesJson = toImagesJson(b.get("images"));
        jdbc.update(
                "UPDATE t_adoption SET title=?, pet_type=?, breed=?, gender=?, age=?, rescue_story=?, " +
                "health_status=?, vaccinated=?, neutered=?, city=?, contact=?, images=? WHERE id=?",
                str(b.get("title")),
                str(b.get("petType")),
                str(b.get("breed")),
                num(b.get("gender"), 0),
                str(b.get("age")),
                str(b.get("rescueStory")),
                str(b.get("healthStatus")),
                bool(b.get("vaccinated")) ? 1 : 0,
                bool(b.get("neutered")) ? 1 : 0,
                str(b.get("city")),
                str(b.get("contact")),
                imagesJson,
                id);
        return ApiResp.ok();
    }

    // ============ 上/下架 ============
    @PostMapping("/{id}/status")
    public ApiResp<Void> setStatus(@PathVariable long id, @RequestBody Map<String, Object> b) {
        adminOnly();
        int status = num(b.get("status"), 1);
        int n = jdbc.update("UPDATE t_adoption SET status = ? WHERE id = ? AND is_deleted = 0", status, id);
        if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在");
        return ApiResp.ok();
    }

    // ============ 删除（软删）============
    @DeleteMapping("/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        adminOnly();
        jdbc.update("UPDATE t_adoption SET is_deleted = 1 WHERE id = ?", id);
        return ApiResp.ok();
    }

    // ============ 申请列表（关联宠物标题 + 申请人）============
    @GetMapping("/applications")
    public ApiResp<List<Map<String, Object>>> applications(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long adoptionId) {
        adminOnly();
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.adoption_id AS adoptionId, a.user_id AS userId, " +
                "a.name AS applicantName, a.phone AS applicantPhone, " +
                "a.reason, a.status, a.created_at AS createdAt, " +
                "p.title AS adoptionTitle, p.pet_type AS petType, p.images, p.city " +
                "FROM t_adoption_application a JOIN t_adoption p ON a.adoption_id = p.id " +
                "WHERE a.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND a.status = ?");
            args.add(status);
        }
        if (adoptionId != null) {
            sql.append(" AND a.adoption_id = ?");
            args.add(adoptionId);
        }
        sql.append(" ORDER BY a.id DESC");
        return ApiResp.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    // ============ 审核申请（通过/驳回）============
    @PostMapping("/application/{id}/review")
    @Transactional
    public ApiResp<Void> review(@PathVariable long id, @RequestBody Map<String, Object> b) {
        adminOnly();
        boolean approved = Boolean.TRUE.equals(b.get("approved"));
        if (approved) {
            // 1) 通过本申请
            //    必须带 status = 0（待审核）守卫：否则重复点击/并发审核会把同一宠物反复锁定，
            //    甚至在「已驳回」的申请上再次通过，出现一宠两主。
            int n = jdbc.update(
                    "UPDATE t_adoption_application SET status = 1 WHERE id = ? AND status = 0 AND is_deleted = 0", id);
            if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "该申请不存在或已审核，请刷新后重试");
            // 2) 锁定该宠物：置为「已领养」(status=2)，从公开可领养列表下架，避免一宠被多人通过
            Long adoptionId = jdbc.queryForObject(
                    "SELECT adoption_id FROM t_adoption_application WHERE id = ? AND is_deleted = 0", Long.class, id);
            if (adoptionId != null) {
                jdbc.update("UPDATE t_adoption SET status = 2 WHERE id = ? AND is_deleted = 0", adoptionId);
                // 3) 同一宠物的其他待审核申请置为「已拒绝」，闭环领养流程
                jdbc.update(
                        "UPDATE t_adoption_application SET status = 2 WHERE adoption_id = ? AND id != ? " +
                        "AND status = 0 AND is_deleted = 0",
                        adoptionId, id);
            }
        } else {
            int n = jdbc.update(
                    "UPDATE t_adoption_application SET status = 2 WHERE id = ? AND status = 0 AND is_deleted = 0", id);
            if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "该申请不存在或已审核，请刷新后重试");
        }
        return ApiResp.ok();
    }

    // ============ 图片上传 ============
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> upload(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) throws IOException {
        adminOnly();
        if (files == null || files.length == 0) throw new BizException(ErrorCode.PARAM_ERROR, "未选择文件");
        Path root = Paths.get(System.getProperty("user.dir"), "static", "uploads", "adoption");
        Files.createDirectories(root);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String ct = file.getContentType();
            if (ct == null || !ALLOWED.contains(ct)) continue;
            String ext = switch (ct) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".jpg";
            };
            String filename = System.nanoTime() + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
            file.transferTo(root.resolve(filename).toFile());
            urls.add(request.getContextPath() + "/static/uploads/adoption/" + filename);
        }
        if (urls.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "未选择有效图片");
        return ApiResp.ok(Map.of("urls", urls));
    }

    // ============= 工具 =============
    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim();
    }

    private int num(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private boolean bool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() == 1;
        return "1".equals(String.valueOf(v)) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private String toImagesJson(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof List) return objectMapper.writeValueAsString(v);
            if (v instanceof String s && !s.isBlank()) return s;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
