package com.petmate.adoption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/adoption")
public class AdoptionController {

    private static final Logger log = LoggerFactory.getLogger(AdoptionController.class);

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdoptionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/list")
    public ApiResp<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, title, pet_type, breed, gender, age, health_status, vaccinated, neutered, city, images, status " +
            "FROM t_adoption WHERE is_deleted = 0 AND status = 1 ORDER BY id DESC");
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(normalize(r));
        return ApiResp.ok(out);
    }

    @GetMapping("/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, title, pet_type, breed, gender, age, rescue_story, health_status, vaccinated, neutered, city, contact, images, status " +
            "FROM t_adoption WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在");
        return ApiResp.ok(normalize(rows.get(0)));
    }

    @PostMapping("/{id}/apply")
    public ApiResp<Map<String, Object>> apply(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();

        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id FROM t_adoption WHERE id = ? AND is_deleted = 0 AND status = 1", id);
        if (exist.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在或已下架");

        String name = (String) body.get("name");
        String phone = (String) body.get("phone");
        if (name == null || name.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写联系人姓名");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$"))
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写正确的手机号");
        String reason = (String) body.get("reason");
        if (reason != null && reason.length() > 200) reason = reason.substring(0, 200);

        jdbc.update(
            "INSERT INTO t_adoption_application (adoption_id, user_id, name, phone, reason, status) " +
            "VALUES (?, ?, ?, ?, ?, 0)",
            id, uid, name, phone, reason);
        long appId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", appId);
        out.put("status", "pending");
        return ApiResp.ok(out);
    }

    @GetMapping("/my-applications")
    public ApiResp<List<Map<String, Object>>> myApplications() {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.id, a.adoption_id AS adoptionId, a.user_id AS userId, " +
            "a.name AS applicantName, a.phone AS applicantPhone, " +
            "a.reason, a.status, a.created_at AS createdAt, " +
            "p.title AS adoptionTitle, p.pet_type AS petType, p.images, p.city " +
            "FROM t_adoption_application a JOIN t_adoption p ON a.adoption_id = p.id " +
            "WHERE a.user_id = ? AND a.is_deleted = 0 ORDER BY a.id DESC", uid);
        return ApiResp.ok(rows);
    }

    /** 撤销自己的领养申请（仅待审核可撤销；通过乐观更新 is_deleted=1） */
    @PostMapping("/applications/{id}/cancel")
    public ApiResp<Void> cancelApplication(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        // 仅本人 + 待审核(status=0) + 未删 → 软删
        int n = jdbc.update(
            "UPDATE t_adoption_application SET is_deleted = 1, updated_at = NOW() " +
            "WHERE id = ? AND user_id = ? AND status = 0 AND is_deleted = 0",
            id, uid);
        if (n == 0) {
            // 区分原因：已审核过 / 不存在
            Integer status = jdbc.query(
                "SELECT status FROM t_adoption_application WHERE id = ? AND user_id = ? AND is_deleted = 0",
                rs -> rs.next() ? rs.getInt(1) : null, id, uid);
            if (status == null) throw new BizException(ErrorCode.PARAM_ERROR, "申请不存在或已撤销");
            throw new BizException(ErrorCode.PARAM_ERROR, "该申请已进入审核流程，无法撤销");
        }
        return ApiResp.ok();
    }

    // ============ 用户端：图片上传 ============
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> upload(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) throws IOException {
        TokenContext.requireUserId();
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

    // ============ 用户端：发布领养（创建待领养宠物）============
    @PostMapping("")
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        long uid = TokenContext.requireUserId();
        String title = str(b.get("title"));
        if (title == null || title.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写宠物名称/标题");
        String petType = str(b.get("petType"));
        if (petType == null || petType.isBlank()) petType = "other";

        int gender = num(b.get("gender"), 0);
        int vaccinated = bool(b.get("vaccinated")) ? 1 : 0;
        int neutered = bool(b.get("neutered")) ? 1 : 0;
        String imagesJson = toImagesJson(b.get("images"));

        jdbc.update(
            "INSERT INTO t_adoption (user_id, title, pet_type, breed, gender, age, rescue_story, health_status, " +
            "vaccinated, neutered, city, contact, images, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
            uid,
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
            imagesJson);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        return ApiResp.ok(out);
    }

    // ============ 用户端：我的领养管理（归属校验）============

    /** 我发布的领养列表，支持 status / petType / keyword 过滤；返回含 createdAt */
    @GetMapping("/mine")
    public ApiResp<List<Map<String, Object>>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String keyword) {
        long uid = TokenContext.requireUserId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, title, pet_type, breed, gender, age, city, images, status, created_at " +
            "FROM t_adoption WHERE user_id = ? AND is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (status != null && !status.isBlank()) {
            int s = num(status, -1);
            if (s >= 0 && s <= 2) { sql.append(" AND status = ?"); args.add(s); }
        }
        if (petType != null && !petType.isBlank()) { sql.append(" AND pet_type = ?"); args.add(petType); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title LIKE ? OR breed LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw); args.add(kw);
        }
        sql.append(" ORDER BY id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = normalize(r);
            Object ca = r.get("created_at");
            m.put("createdAt", ca == null ? null : ca.toString().replace("T", " "));
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /** 编辑自己发布的领养（仅本人可改） */
    @PutMapping("/{id}")
    public ApiResp<Void> updateMine(@PathVariable long id, @RequestBody Map<String, Object> b) {
        long uid = TokenContext.requireUserId();
        assertOwner(id, uid);
        StringBuilder sql = new StringBuilder("UPDATE t_adoption SET updated_at = NOW()");
        List<Object> args = new ArrayList<>();
        if (b.containsKey("title"))      { sql.append(", title = ?");        args.add(str(b.get("title"))); }
        if (b.containsKey("petType"))    { sql.append(", pet_type = ?");     args.add(str(b.get("petType"))); }
        if (b.containsKey("breed"))      { sql.append(", breed = ?");        args.add(str(b.get("breed"))); }
        if (b.containsKey("gender"))     { sql.append(", gender = ?");       args.add(num(b.get("gender"), 0)); }
        if (b.containsKey("age"))        { sql.append(", age = ?");          args.add(str(b.get("age"))); }
        if (b.containsKey("rescueStory")){ sql.append(", rescue_story = ?"); args.add(str(b.get("rescueStory"))); }
        if (b.containsKey("healthStatus")){sql.append(", health_status = ?");args.add(str(b.get("healthStatus"))); }
        if (b.containsKey("vaccinated")) { sql.append(", vaccinated = ?");   args.add(bool(b.get("vaccinated")) ? 1 : 0); }
        if (b.containsKey("neutered"))   { sql.append(", neutered = ?");     args.add(bool(b.get("neutered")) ? 1 : 0); }
        if (b.containsKey("city"))       { sql.append(", city = ?");         args.add(str(b.get("city"))); }
        if (b.containsKey("contact"))    { sql.append(", contact = ?");      args.add(str(b.get("contact"))); }
        if (b.containsKey("images"))     { sql.append(", images = ?");       args.add(toImagesJson(b.get("images"))); }
        sql.append(" WHERE id = ? AND user_id = ? AND is_deleted = 0");
        args.add(id); args.add(uid);
        jdbc.update(sql.toString(), args.toArray());
        return ApiResp.ok();
    }

    /** 删除自己发布的领养（软删） */
    @DeleteMapping("/{id}")
    public ApiResp<Void> deleteMine(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        int n = jdbc.update(
            "UPDATE t_adoption SET is_deleted = 1, updated_at = NOW() " +
            "WHERE id = ? AND user_id = ? AND is_deleted = 0", id, uid);
        if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在或无权操作");
        return ApiResp.ok();
    }

    /** 手动更新状态：0下架 / 1待领养 / 2已领养（仅本人） */
    @PostMapping("/{id}/status")
    public ApiResp<Void> setStatusMine(@PathVariable long id, @RequestBody Map<String, Object> b) {
        long uid = TokenContext.requireUserId();
        assertOwner(id, uid);
        int status = num(b.get("status"), -1);
        if (status < 0 || status > 2) throw new BizException(ErrorCode.PARAM_ERROR, "无效的状态值");
        jdbc.update(
            "UPDATE t_adoption SET status = ?, updated_at = NOW() " +
            "WHERE id = ? AND user_id = ? AND is_deleted = 0", status, id, uid);
        return ApiResp.ok();
    }

    /** 归属校验：返回信息不属于当前用户或无记录则抛异常 */
    private void assertOwner(long id, long uid) {
        Integer owner = jdbc.query(
            "SELECT user_id FROM t_adoption WHERE id = ? AND is_deleted = 0",
            rs -> rs.next() ? rs.getInt(1) : null, id);
        if (owner == null) throw new BizException(ErrorCode.PARAM_ERROR, "领养信息不存在");
        if (owner.longValue() != uid) throw new BizException(ErrorCode.PARAM_ERROR, "无权操作该领养信息");
    }

    private Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("title", r.get("title"));
        m.put("petType", r.get("pet_type"));
        m.put("breed", r.get("breed"));
        m.put("gender", r.get("gender"));
        m.put("age", r.get("age"));
        m.put("healthStatus", r.get("health_status"));
        m.put("vaccinated", r.get("vaccinated") != null && ((Number) r.get("vaccinated")).intValue() == 1);
        m.put("neutered", r.get("neutered") != null && ((Number) r.get("neutered")).intValue() == 1);
        m.put("city", r.get("city"));
        m.put("contact", r.get("contact"));
        m.put("images", r.get("images"));
        m.put("status", r.get("status"));
        if (r.containsKey("rescue_story")) m.put("rescueStory", r.get("rescue_story"));
        return m;
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
