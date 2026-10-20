package com.petmate.merchant;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商家申请入驻（匿名 + 公开状态查询）
 *
 *   POST /merchant/apply          提交入驻申请
 *   GET  /merchant/apply/status   公开查询某手机号的申请状态
 *
 * 审核接口在 AdminController: GET /admin/merchant/apply/list + POST /admin/merchant/apply/audit
 */
@RestController
public class MerchantApplyController {

    private final JdbcTemplate jdbc;

    public MerchantApplyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final List<String> ALLOWED_TYPES = List.of(
        "grooming", "medical", "boarding", "comprehensive"
    );

    /**
     * 提交入驻申请（匿名访问,不需登录）
     * body: { phone, password, ownerName, idCard, storeName, type, address,
     *         lng?, lat?, businessHours?, photos?, licenseNo?, remark? }
     */
    @PostMapping("/merchant/apply")
    public ApiResp<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        // ===== 必填校验 =====
        String phone = asString(body.get("phone"));
        String password = asString(body.get("password"));
        String ownerName = asString(body.get("ownerName"));
        String idCard = asString(body.get("idCard"));
        String storeName = asString(body.get("storeName"));
        String type = asString(body.get("type"));
        // 详细地址：可选（页面已改为非必填），允许为空串
        String address = asStringOrEmpty(body.get("address"));

        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入正确的 11 位手机号");
        }
        if (password == null || password.length() < 6) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码至少 6 位");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "店主姓名不能为空");
        }
        // 身份证号：必填，支持 15 位旧版或 18 位（末位可为 X）
        if (idCard == null || !idCard.matches("^(\\d{15}|\\d{17}[\\dXx])$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入正确的身份证号");
        }
        if (storeName == null || storeName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "门店名称不能为空");
        }
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "门店类型不合法");
        }
        // 企业名称：必填
        String enterpriseName = asString(body.get("enterpriseName"));
        if (enterpriseName == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "企业名称不能为空");
        }
        // 统一社会信用代码 / 营业执照号：必填（前端 licenseNo 会同时映射 licenseNo 与 unifiedCreditCode）
        String unifiedCreditCode = asString(body.get("unifiedCreditCode"));
        if (unifiedCreditCode == null) {
            unifiedCreditCode = asString(body.get("licenseNo"));
        }
        if (unifiedCreditCode == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "统一社会信用代码 / 营业执照号不能为空");
        }

        // ===== 唯一性:同一手机号不可重复申请,且 t_user 里没注册过 =====
        Integer phoneExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_apply WHERE phone = ?", Integer.class, phone);
        if (phoneExists != null && phoneExists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该手机号已提交过申请,请前往申请状态页查询进度");
        }
        Integer userExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user WHERE phone = ? AND is_deleted = 0", Integer.class, phone);
        if (userExists != null && userExists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该手机号已注册,请直接登录");
        }

        // ===== 密码 BCrypt 加密 =====
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));

        // ===== 写库 =====
        jdbc.update(
            "INSERT INTO t_merchant_apply " +
            "(phone, password_hash, owner_name, id_card, store_name, type, address, " +
            " lng, lat, business_hours, photos, license_no, remark, status, " +
            " enterprise_name, store_phone, business_license_url, unified_credit_code) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
            phone, passwordHash, ownerName, idCard, storeName, type, address,
            asBigDecimalOrNull(body.get("lng")),
            asBigDecimalOrNull(body.get("lat")),
            asString(body.get("businessHours")),
            asString(body.get("photos")),
            unifiedCreditCode,
            asString(body.get("remark")),
            enterpriseName,
            asString(body.get("storePhone")),
            asString(body.get("businessLicenseUrl")),
            unifiedCreditCode);

        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", 0);
        return ApiResp.ok(out);
    }

    /**
     * 营业执照 / 资质图片上传（匿名,不需登录）
     * body: multipart file
     * 返回 { url } 形如 /api/v1/static/uploads/merchant-apply/lic_xxx.jpg
     */
    @PostMapping(value = "/merchant/apply/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> uploadLicense(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未选择文件");
        }
        String ct = file.getContentType();
        if (ct == null || !List.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(ct)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 JPG / PNG / WebP / GIF 图片");
        }
        String ext = switch (ct) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String filename = "lic_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path root = Paths.get(System.getProperty("user.dir"), "static", "uploads", "merchant-apply");
        Files.createDirectories(root);
        file.transferTo(root.resolve(filename).toFile());
        String url = request.getContextPath() + "/static/uploads/merchant-apply/" + filename;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        return ApiResp.ok(out);
    }

    /**
     * 查询自己手机号的入驻申请状态(R3.11 安全加固:要求已登录,且 phone 必须等于当前用户手机)
     * GET /merchant/apply/status?phone=
     */
    @GetMapping("/merchant/apply/status")
    public ApiResp<Map<String, Object>> status(@RequestParam String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式错误");
        }
        // R3.11: 必须已登录,且查询的 phone 必须等于当前用户手机号,防止任意访客枚举手机号
        // 读取店主手机号（员工走店主 merchant_id）
        long uid = TokenContext.requireUserId();
        String myPhone = jdbc.queryForObject(
            "SELECT phone FROM t_user WHERE id = ? AND is_deleted = 0", String.class, uid);
        if (myPhone == null || !myPhone.equals(phone)) {
            throw new BizException(ErrorCode.NO_PERMISSION, "仅可查询自己的入驻申请状态");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, phone, owner_name, store_name, type, status, submit_at, reviewed_at, review_note " +
            "FROM t_merchant_apply WHERE phone = ?", phone);
        Map<String, Object> out = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            // 没申请过,顺手看下是不是已经注册成商家了
            Integer userCnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE phone = ? AND is_deleted = 0", Integer.class, phone);
            out.put("exists", false);
            out.put("isMerchant", userCnt != null && userCnt > 0);
        } else {
            Map<String, Object> r = rows.get(0);
            out.put("exists", true);
            out.put("id", r.get("id"));
            out.put("phone", r.get("phone"));
            out.put("ownerName", r.get("owner_name"));
            out.put("storeName", r.get("store_name"));
            out.put("type", r.get("type"));
            out.put("status", r.get("status"));
            out.put("submitAt", r.get("submit_at") == null ? null : r.get("submit_at").toString());
            out.put("reviewedAt", r.get("reviewed_at") == null ? null : r.get("reviewed_at").toString());
            out.put("reviewNote", r.get("review_note"));
        }
        return ApiResp.ok(out);
    }

    // ===== helpers =====
    private static String asString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    // 可选字段：保留空串（用于 NOT NULL 列,如 address 允许空串但不可为 null）
    private static String asStringOrEmpty(Object o) {
        if (o == null) return "";
        return String.valueOf(o).trim();
    }

    private static java.math.BigDecimal asBigDecimalOrNull(Object o) {
        if (o == null) return null;
        try { return new java.math.BigDecimal(String.valueOf(o)); }
        catch (Exception e) { return null; }
    }
}
