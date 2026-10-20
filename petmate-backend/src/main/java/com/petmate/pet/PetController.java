package com.petmate.pet;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

@RestController
public class PetController {

    private static final Logger log = LoggerFactory.getLogger(PetController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public PetController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ================ 宠物 ================

    @GetMapping("/pet/list")
    public ApiResp<List<Map<String, Object>>> list() {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.user_id, p.name, p.category_id, p.breed_id, p.breed_name, " +
            "c.name AS category_name, b.name AS breed_name_joined, " +
            "p.gender, p.birthday, p.weight, p.neutered, p.avatar, p.tags, p.description " +
            "FROM t_pet p " +
            "LEFT JOIN t_pet_category c ON p.category_id = c.id " +
            "LEFT JOIN t_pet_breed b ON p.breed_id = b.id " +
            "WHERE p.user_id = ? AND p.is_deleted = 0 ORDER BY p.id DESC", uid);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            try {
                out.add(normalizePet(row));
            } catch (Exception e) {
                // 单行解析失败不阻塞整个列表
                log.warn("normalize pet row failed, uid={}, row={}", uid, row, e);
            }
        }
        return ApiResp.ok(out);
    }

    @GetMapping("/pet/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.user_id, p.name, p.category_id, p.breed_id, p.breed_name, " +
            "c.name AS category_name, b.name AS breed_name_joined, " +
            "p.gender, p.birthday, p.weight, p.neutered, p.avatar, p.tags, p.description " +
            "FROM t_pet p " +
            "LEFT JOIN t_pet_category c ON p.category_id = c.id " +
            "LEFT JOIN t_pet_breed b ON p.breed_id = b.id " +
            "WHERE p.id = ? AND p.user_id = ? AND p.is_deleted = 0", id, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "宠物不存在");
        return ApiResp.ok(normalizePet(rows.get(0)));
    }

    @PostMapping("/pet")
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResp.ok(doSave(body, null));
    }

    @PutMapping("/pet/{id}")
    public ApiResp<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResp.ok(doSave(body, id));
    }

    @DeleteMapping("/pet/{id}")
    public ApiResp<Map<String, Object>> delete(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        // 1) 检查宠物是否存在以及归属
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT is_deleted FROM t_pet WHERE id = ? AND user_id = ?", id, uid);
        if (rows.isEmpty()) {
            // 不存在 / 不属于当前用户
            throw new BizException(ErrorCode.PARAM_ERROR, "宠物不存在");
        }
        int isDeleted = ((Number) rows.get(0).get("is_deleted")).intValue();
        if (isDeleted == 1) {
            // 幂等:已删除,直接告诉前端成功即可
            return ApiResp.ok(Map.of("id", id, "idempotent", true));
        }
        // 2) 软删宠物 + 级联软删健康记录
        jdbc.update("UPDATE t_pet SET is_deleted = 1 WHERE id = ? AND user_id = ?", id, uid);
        jdbc.update("UPDATE t_pet_health_record SET is_deleted = 1 WHERE pet_id = ?", id);
        return ApiResp.ok(Map.of("id", id, "idempotent", false));
    }

    // ================ 健康记录 ================

    @GetMapping("/pet/{id}/health")
    public ApiResp<List<Map<String, Object>>> health(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        assertOwnership(id, uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, pet_id, record_type, title, content, record_date, next_date, reminder " +
            "FROM t_pet_health_record WHERE pet_id = ? AND is_deleted = 0 ORDER BY record_date DESC, id DESC", id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(normalizeHealth(r));
        return ApiResp.ok(out);
    }

    /**
     * 健康摘要:聚合 vaccine/deworming 完成状态 + 记录列表,
     * 前端宠物详情页单次拉取即可。
     */
    @GetMapping("/pet/{id}/health/summary")
    public ApiResp<Map<String, Object>> healthSummary(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        assertOwnership(id, uid);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, pet_id, record_type, title, content, record_date, next_date, reminder " +
            "FROM t_pet_health_record WHERE pet_id = ? AND is_deleted = 0 ORDER BY record_date DESC, id DESC", id);

        int vaccineCount = 0, dewormCount = 0;
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String t = (String) r.get("record_type");
            if ("vaccine".equals(t)) vaccineCount++;
            else if ("deworming".equals(t)) dewormCount++;
            records.add(normalizeHealth(r));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("petId", id);
        out.put("vaccineDone", vaccineCount > 0);
        out.put("dewormDone", dewormCount > 0);
        out.put("vaccineCount", vaccineCount);
        out.put("dewormCount", dewormCount);
        out.put("records", records);
        return ApiResp.ok(out);
    }

    @PostMapping("/pet/health")
    public ApiResp<Map<String, Object>> addHealth(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        long petId = ((Number) body.get("petId")).longValue();
        assertOwnership(petId, uid);

        String recordType = (String) body.get("recordType");
        if (recordType == null || recordType.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "recordType 必填");
        String title = (String) body.get("title");
        if (title == null || title.isBlank())
            throw new BizException(ErrorCode.PARAM_ERROR, "title 必填");

        jdbc.update(
            "INSERT INTO t_pet_health_record (pet_id, record_type, title, content, record_date, next_date, reminder) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            petId, recordType, title, body.get("content"),
            Date.valueOf((String) body.get("date")),
            body.get("nextDate") != null ? Date.valueOf((String) body.get("nextDate")) : null,
            Boolean.TRUE.equals(body.get("reminder")) ? 1 : 0);

        // 录入健康记录即生成关怀提醒（与市面 App "记录即提醒"一致）
        Long reminderId = null;
        if (Boolean.TRUE.equals(body.get("reminder")) && body.get("nextDate") != null) {
            String rType = "custom";
            if ("vaccine".equals(recordType)) rType = "vaccine";
            else if ("deworming".equals(recordType)) rType = "deworming";
            else if ("checkup".equals(recordType)) rType = "checkup";

            // 提前提醒天数(默认 3)与循环周期(默认 0=一次性)均可由前端指定，
            // 周期>0 时标记完成会自动生成下一次（驱虫每 90 天这类场景）。
            int advanceDays = optInt(body.get("advanceDays"), 3);
            if (advanceDays < 0) advanceDays = 0;
            if (advanceDays > 30) advanceDays = 30;
            int cycleDays = optInt(body.get("cycleDays"), 0);
            if (cycleDays < 0) cycleDays = 0;

            jdbc.update(
                "INSERT INTO t_pet_reminder (pet_id, user_id, type, title, due_date, advance_days, cycle_days, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
                petId, uid, rType, title, Date.valueOf((String) body.get("nextDate")), advanceDays, cycleDays);
            reminderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("petId", petId);
        if (reminderId != null) out.put("reminderId", reminderId);
        return ApiResp.ok(out);
    }

    /** 宽松取整型参数：null / 非法值回落到默认值 */
    private int optInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    // ================ 字典 ================
    @GetMapping("/pet/categories")
    public ApiResp<List<Map<String, Object>>> categories() {
        return ApiResp.ok(jdbc.queryForList("SELECT id, name, icon, sort FROM t_pet_category ORDER BY sort, id"));
    }

    @GetMapping("/pet/breeds")
    public ApiResp<List<Map<String, Object>>> breeds(@RequestParam(required = false) Long categoryId) {
        String sql = "SELECT id, category_id, name FROM t_pet_breed" +
                     (categoryId == null ? "" : " WHERE category_id = ?") + " ORDER BY id";
        return ApiResp.ok(jdbc.queryForList(
            sql, categoryId == null ? new Object[]{} : new Object[]{categoryId}));
    }

    // ================ 内部工具 ================

    private void assertOwnership(long petId, long uid) {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_pet WHERE id = ? AND user_id = ? AND is_deleted = 0",
            Integer.class, petId, uid);
        if (cnt == null || cnt == 0)
            throw new BizException(ErrorCode.PARAM_ERROR, "宠物不存在或已删除");
    }

    /**
     * 头像字段归一化:
     *  - 空/null:返回 null
     *  - 以 http(s):// 开头的外链:原样保留
     *  - 以 data:image/...;base64, 开头的:限制 base64 长度 2MB(≈ 1.5MB 解码图),
     *    若超过 2MB 抛业务异常
     *  - 其他字符一律视为非法,返回 null(避免把脏数据塞进库)
     */
    private String sanitizeAvatar(String avatar) {
        if (avatar == null) return null;
        avatar = avatar.trim();
        if (avatar.isEmpty()) return null;
        if (avatar.startsWith("http://") || avatar.startsWith("https://")) {
            return avatar.length() > 2048 ? avatar.substring(0, 2048) : avatar;
        }
        if (avatar.startsWith("data:image/") && avatar.contains(";base64,")) {
            if (avatar.length() > 2 * 1024 * 1024) {
                throw new BizException(ErrorCode.PARAM_ERROR, "头像图片过大,请压缩到 1.5MB 以下");
            }
            return avatar;
        }
        log.warn("avatar field rejected (not URL nor data URI)");
        return null;
    }

    /** 校验 + 入库宠物主表(创建时还会触发初始健康记录) */
    private Map<String, Object> doSave(Map<String, Object> body, Long id) {
        long uid = TokenContext.requireUserId();

        // ===== 参数校验 =====
        String name = (String) body.get("name");
        if (name == null || name.trim().isEmpty())
            throw new BizException(ErrorCode.PARAM_ERROR, "宠物姓名必填");
        name = name.trim();
        if (name.length() > 20)
            throw new BizException(ErrorCode.PARAM_ERROR, "宠物姓名不超过 20 个字符");

        Object categoryIdRaw = body.get("categoryId");
        Object breedIdRaw = body.get("breedId");
        if (categoryIdRaw == null || breedIdRaw == null)
            throw new BizException(ErrorCode.PARAM_ERROR, "categoryId/breedId 必填");
        long categoryId = ((Number) categoryIdRaw).longValue();
        long breedId = ((Number) breedIdRaw).longValue();

        // 自定义品种：breedId = -1（"其他"），使用用户手填的品种名称
        boolean isCustomBreed = breedId == -1;
        String breedName = null;
        if (isCustomBreed) {
            Object bnRaw = body.get("breedName");
            String bn = bnRaw == null ? null : bnRaw.toString().trim();
            if (bn == null || bn.isEmpty())
                throw new BizException(ErrorCode.PARAM_ERROR, "请填写品种名称");
            if (bn.length() > 64)
                throw new BizException(ErrorCode.PARAM_ERROR, "品种名称不超过 64 字符");
            // 校验分类存在（无需匹配具体品种）
            Integer catOk = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_pet_category WHERE id = ?", Integer.class, categoryId);
            if (catOk == null || catOk == 0)
                throw new BizException(ErrorCode.PARAM_ERROR, "宠物分类不存在");
            breedName = bn;
        } else {
            // 校验品种属于该分类
            Integer breedOk = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_pet_breed WHERE id = ? AND category_id = ?",
                Integer.class, breedId, categoryId);
            if (breedOk == null || breedOk == 0)
                throw new BizException(ErrorCode.PARAM_ERROR, "品种与分类不匹配");
        }

        // gender
        Object genderRaw = body.get("gender");
        int gender;
        if (genderRaw instanceof Number) {
            gender = ((Number) genderRaw).intValue();
        } else if (genderRaw instanceof String && !((String) genderRaw).isEmpty()) {
            try { gender = Integer.parseInt((String) genderRaw); }
            catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "gender 非法");
            }
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "gender 必填(0/1/2)");
        }
        if (gender != 0 && gender != 1 && gender != 2)
            throw new BizException(ErrorCode.PARAM_ERROR, "gender 必填(0/1/2)");

        // birthday: 不可晚于今天
        Date birthday = null;
        if (body.get("birthday") != null && !((String) body.get("birthday")).isEmpty()) {
            try {
                LocalDate d = LocalDate.parse((String) body.get("birthday"));
                if (d.isAfter(LocalDate.now()))
                    throw new BizException(ErrorCode.PARAM_ERROR, "出生日期不能晚于今天");
                birthday = Date.valueOf(d);
            } catch (java.time.format.DateTimeParseException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "出生日期格式需为 yyyy-MM-dd");
            }
        }

        // weight: 0 < w <= 200
        BigDecimal weight = null;
        if (body.get("weight") != null) {
            try {
                BigDecimal w = new BigDecimal(body.get("weight").toString());
                if (w.compareTo(BigDecimal.ZERO) <= 0 || w.compareTo(new BigDecimal("200")) > 0)
                    throw new BizException(ErrorCode.PARAM_ERROR, "体重需在 0~200 kg 之间");
                weight = w;
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "体重格式错误");
            }
        }

        // neutered: 0/1 (兼容前端传布尔 true/false 或数字 1/0)
        Object neoRaw = body.get("neutered");
        int neutered = 0;
        if (neoRaw instanceof Boolean b) {
            neutered = b ? 1 : 0;
        } else if (neoRaw instanceof Number n) {
            neutered = n.intValue() != 0 ? 1 : 0;
        } else if (neoRaw != null) {
            String neoStr = neoRaw.toString().trim().toLowerCase();
            neutered = !"0".equals(neoStr) && !"false".equals(neoStr) ? 1 : 0;
        }

        // avatar: 仅允许外链 URL 或 base64 data URI,长度上限 2MB(≈ 1.5MB 解码图)
        // 头像列已是 MEDIUMTEXT(16MB 上限),这里多一层防御避免请求体过大
        String avatar = (String) body.get("avatar");
        avatar = sanitizeAvatar(avatar);

        // tags: JSON 数组
        String tagsJson = null;
        if (body.get("tags") != null) {
            try {
                tagsJson = json.writeValueAsString(body.get("tags"));
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "tags 序列化失败");
            }
        }

        String description = (String) body.get("description");
        if (description != null && description.length() > 1000)
            throw new BizException(ErrorCode.PARAM_ERROR, "描述不超过 1000 字符");

        // ===== 入库 =====
        if (id == null) {
            jdbc.update(
                "INSERT INTO t_pet (user_id, name, category_id, breed_id, breed_name, gender, birthday, weight, neutered, avatar, tags, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                uid, name, categoryId, breedId, breedName, gender, birthday, weight, neutered, avatar, tagsJson, description);
            id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            // 初值健康档案(可选)
            bootstrapHealthRecords(id, body);
        } else {
            // 修改时校验所有权
            assertOwnership(id, uid);
            jdbc.update(
                "UPDATE t_pet SET name=?, category_id=?, breed_id=?, breed_name=?, gender=?, birthday=?, weight=?, neutered=?, avatar=?, tags=?, description=? " +
                "WHERE id = ? AND user_id = ?",
                name, categoryId, breedId, breedName, gender, birthday, weight, neutered, avatar, tagsJson, description,
                id, uid);
        }

        // 返回最新行
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.id, p.user_id, p.name, p.category_id, p.breed_id, p.breed_name, " +
            "c.name AS category_name, b.name AS breed_name_joined, " +
            "p.gender, p.birthday, p.weight, p.neutered, p.avatar, p.tags, p.description " +
            "FROM t_pet p " +
            "LEFT JOIN t_pet_category c ON p.category_id = c.id " +
            "LEFT JOIN t_pet_breed b ON p.breed_id = b.id " +
            "WHERE p.id = ?", id);
        return normalizePet(rows.get(0));
    }

    /** 创建档案时一次性写入初始健康记录 */
    private void bootstrapHealthRecords(long petId, Map<String, Object> body) {
        Boolean vaccineDone = toBooleanOrNull(body.get("vaccineDone"));
        Boolean dewormDone = toBooleanOrNull(body.get("dewormDone"));
        String allergies = (String) body.get("allergies");
        Date today = Date.valueOf(LocalDate.now());

        if (Boolean.TRUE.equals(vaccineDone)) {
            jdbc.update(
                "INSERT INTO t_pet_health_record (pet_id, record_type, title, content, record_date, reminder) " +
                "VALUES (?, 'vaccine', ?, ?, ?, 1)",
                petId, "基础疫苗接种", "档案建立时已勾选完成", today);
        }
        if (Boolean.TRUE.equals(dewormDone)) {
            jdbc.update(
                "INSERT INTO t_pet_health_record (pet_id, record_type, title, content, record_date, reminder) " +
                "VALUES (?, 'deworming', ?, ?, ?, 1)",
                petId, "体内驱虫", "档案建立时已勾选完成", today);
        }
        if (allergies != null && !allergies.isBlank()) {
            jdbc.update(
                "INSERT INTO t_pet_health_record (pet_id, record_type, title, content, record_date) " +
                "VALUES (?, 'illness', ?, ?, ?)",
                petId, "过敏史登记", allergies.trim(), today);
        }
    }

    private Boolean toBooleanOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() == 1;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return null;
    }

    private Map<String, Object> normalizePet(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("userId", row.get("user_id"));
        m.put("name", row.get("name"));
        m.put("categoryId", row.get("category_id"));
        m.put("breedId", row.get("breed_id"));
        m.put("categoryName", row.get("category_name"));
        // 自定义品种优先使用 t_pet.breed_name，否则回退到关联的 t_pet_breed.name
        String customBreedName = row.get("breed_name") == null ? null : row.get("breed_name").toString().trim();
        if (customBreedName == null || customBreedName.isEmpty()) {
            Object joined = row.get("breed_name_joined");
            customBreedName = joined == null ? null : joined.toString();
        }
        m.put("breedName", customBreedName);

        // gender 整型化
        Object g = row.get("gender");
        if (g instanceof Number) m.put("gender", ((Number) g).intValue());
        else if (g != null) m.put("gender", Integer.parseInt(g.toString()));

        // birthday 字符串化
        Object bd = row.get("birthday");
        if (bd instanceof Date) m.put("birthday", bd.toString());
        else if (bd != null) m.put("birthday", bd.toString());

        // weight 优先转 BigDecimal -> double
        Object w = row.get("weight");
        if (w instanceof BigDecimal) m.put("weight", ((BigDecimal) w).doubleValue());
        else if (w instanceof Number) m.put("weight", ((Number) w).doubleValue());

        // neutered 整型化
        Object n = row.get("neutered");
        if (n instanceof Number) m.put("neutered", ((Number) n).intValue());
        else if (n != null) m.put("neutered", Integer.parseInt(n.toString()));

        m.put("avatar", row.get("avatar"));

        // tags 字段:JSON 字符串 → 数组(任何类型都不能把整行炸掉)
        Object rawTags = row.get("tags");
        if (rawTags != null) {
            String tagsStr = rawTags.toString();
            if (!tagsStr.isBlank() && !"null".equalsIgnoreCase(tagsStr)) {
                try {
                    List<String> tags = json.readValue(tagsStr, new TypeReference<List<String>>() {});
                    if (tags != null && !tags.isEmpty()) m.put("tags", tags);
                } catch (Exception e) {
                    log.warn("tags parse failed: {}", tagsStr, e);
                }
            }
        }

        m.put("description", row.get("description"));
        return m;
    }

    private Map<String, Object> normalizeHealth(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("petId", r.get("pet_id"));
        m.put("recordType", r.get("record_type"));
        m.put("title", r.get("title"));
        m.put("content", r.get("content"));
        m.put("date", r.get("record_date").toString());
        if (r.get("next_date") != null) m.put("nextDate", r.get("next_date").toString());
        m.put("reminder", ((Number) r.get("reminder")).intValue() == 1);
        return m;
    }

    // ================ 成长日记照片 ================

    /** 上传宠物成长日记照片（多张），返回每条的 url */
    @PostMapping(value = "/pet/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<List<Map<String, Object>>> uploadPhotos(
            @PathVariable long id,
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) throws IOException {
        long uid = TokenContext.requireUserId();
        assertOwnership(id, uid);
        if (files == null || files.length == 0) throw new BizException(ErrorCode.PARAM_ERROR, "未选择文件");
        if (files.length > 9) throw new BizException(ErrorCode.PARAM_ERROR, "最多上传 9 张");

        Path uploadRoot = Paths.get(System.getProperty("user.dir"), "static", "uploads", "pet");
        Files.createDirectories(uploadRoot);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String ct = file.getContentType();
            if (ct == null || !(ct.equals("image/jpeg") || ct.equals("image/png") || ct.equals("image/webp")))
                throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp");
            if (file.getSize() > 5 * 1024 * 1024)
                throw new BizException(ErrorCode.PARAM_ERROR, "单张图片不能超过 5MB");
            String ext = ct.equals("image/png") ? ".png" : ct.equals("image/webp") ? ".webp" : ".jpg";
            String filename = "pet" + id + "_" + uid + "_" + System.currentTimeMillis() + "_" + out.size() + ext;
            file.transferTo(uploadRoot.resolve(filename).toFile());
            String url = request.getContextPath() + "/static/uploads/pet/" + filename;
            jdbc.update(
                "INSERT INTO t_pet_photo (pet_id, url, caption) VALUES (?, ?, ?)",
                id, url, null);
            long pid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pid);
            m.put("petId", id);
            m.put("url", url);
            m.put("caption", "");
            out.add(m);
        }
        if (out.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "未选择有效文件");
        return ApiResp.ok(out);
    }

    /** 列出宠物全部成长日记照片 */
    @GetMapping("/pet/{id}/photos")
    public ApiResp<List<Map<String, Object>>> listPhotos(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        assertOwnership(id, uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, pet_id, url, caption, created_at FROM t_pet_photo " +
            "WHERE pet_id = ? AND is_deleted = 0 ORDER BY id DESC", id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("petId", r.get("pet_id"));
            m.put("url", r.get("url"));
            m.put("caption", r.get("caption"));
            m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /** 删除单张成长日记照片 */
    @DeleteMapping("/pet/photo/{photoId}")
    public ApiResp<Void> deletePhoto(@PathVariable long photoId) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.pet_id FROM t_pet_photo p JOIN t_pet pt ON p.pet_id = pt.id " +
            "WHERE p.id = ? AND pt.user_id = ? AND p.is_deleted = 0", photoId, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "照片不存在");
        jdbc.update("UPDATE t_pet_photo SET is_deleted = 1 WHERE id = ?", photoId);
        return ApiResp.ok();
    }

    /** 批量删除成长日记照片（仅删除属于当前用户宠物的照片，防越权） */
    @PostMapping("/pet/photos/batch-delete")
    public ApiResp<Map<String, Object>> batchDeletePhotos(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Object raw = body.get("photoIds");
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要删除的照片");
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (Object o : (List<?>) raw) ids.add(((Number) o).longValue());
        StringBuilder ph = new StringBuilder();
        List<Object> args = new java.util.ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) ph.append(",");
            ph.append("?");
            args.add(ids.get(i));
        }
        args.add(uid);
        int deleted = jdbc.update(
            "UPDATE t_pet_photo SET is_deleted = 1 WHERE id IN (" + ph + ") " +
            "AND pet_id IN (SELECT id FROM t_pet WHERE user_id = ?) AND is_deleted = 0",
            args.toArray());
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("deleted", deleted);
        return ApiResp.ok(out);
    }
}
