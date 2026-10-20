package com.petmate.auth;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.JwtService;
import com.petmate.security.TokenBlacklist;
import com.petmate.security.TokenContext;
import com.petmate.user.User;
import com.petmate.user.UserService;
import io.jsonwebtoken.Claims;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口（PRD §7.5 / auth 模块）
 *  POST /auth/login          登录(密码)
 *  POST /auth/logout         登出（写 TokenBlacklist）
 *  GET  /user/me             当前用户信息
 */
@RestController
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final TokenBlacklist tokenBlacklist;

    @Value("${petmate.jwt.expire-seconds}")
    private long expireSeconds;

    public AuthController(UserService userService, JwtService jwtService, JdbcTemplate jdbc,
                          TokenBlacklist tokenBlacklist) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.tokenBlacklist = tokenBlacklist;
    }

    /** 登录（仅支持密码登录：手机号 + 密码） */
    @PostMapping("/auth/login")
    public ApiResp<Map<String, Object>> login(@RequestBody Map<String, Object> body,
                                              jakarta.servlet.http.HttpServletRequest req) {
        String type = (String) body.get("loginType");
        String phone = (String) body.get("phone");
        String password = (String) body.get("password");

        // 员工（子账号）登录：username + password
        if ("staff".equals(type)) {
            return staffLogin(body, req);
        }

        User user;
        if ("password".equals(type)) {
            user = userService.findByPhone(phone);
            if (user == null || !userService.checkPassword(user, password)) {
                throw new BizException(ErrorCode.PASSWORD_ERROR);
            }
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的登录方式");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }

        String role = roleOf(user.getId());
        String token = jwtService.issue(user.getId(), role, user.getId(), "OWNER", null);
        userService.markLogin(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", UserService.toAbsoluteAvatar(req, user.getAvatar()));
        data.put("vipLevel", user.getVipLevel());
        data.put("role", role);
        data.put("subRole", "OWNER");
        data.put("merchantId", user.getId());
        data.put("staffId", null);
        data.put("isStaff", false);
        return ApiResp.ok(data);
    }

    /** 注册：创建账号并直接登录（返回结构与 login 一致，前端注册后自动登录） */
    @PostMapping("/auth/register")
    public ApiResp<Map<String, Object>> register(@RequestBody Map<String, Object> body,
                                                 jakarta.servlet.http.HttpServletRequest req) {
        String phone = (String) body.get("phone");
        String nickname = (String) body.get("nickname");
        String password = (String) body.get("password");
        User user = userService.register(phone, nickname, password);
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        String role = roleOf(user.getId());
        String token = jwtService.issue(user.getId(), role, user.getId(), "OWNER", null);
        userService.markLogin(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("avatar", UserService.toAbsoluteAvatar(req, user.getAvatar()));
        data.put("vipLevel", user.getVipLevel());
        data.put("role", role);
        data.put("subRole", "OWNER");
        data.put("merchantId", user.getId());
        data.put("staffId", null);
        data.put("isStaff", false);
        return ApiResp.ok(data);
    }

    /** 找回密码（演示态：以手机号定位账号并用 BCrypt 重置密码，无短信验证码环节） */
    @PostMapping("/auth/reset-password")
    public ApiResp<Void> resetPassword(@RequestBody Map<String, Object> body) {
        String phone = (String) body.get("phone");
        String password = (String) body.get("password");
        if (phone == null || phone.isBlank() || password == null || password.length() < 6) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入手机号与至少 6 位的新密码");
        }
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_user WHERE phone = ?", Integer.class, phone);
        if (cnt == null || cnt == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该手机号尚未注册");
        }
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        jdbc.update("UPDATE t_user SET password = ? WHERE phone = ?", hash, phone);
        return ApiResp.ok();
    }

    /** 登出：将当前 token 的 jti 加入黑名单 */
    @PostMapping("/auth/logout")
    public ApiResp<Void> logout(jakarta.servlet.http.HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            Claims c = jwtService.parse(token);
            if (c != null && c.getId() != null && c.getExpiration() != null) {
                long uid = 0;
                try { uid = Long.parseLong(c.getSubject()); } catch (Exception ignore) {}
                tokenBlacklist.revoke(c.getId(), c.getExpiration().getTime() / 1000, uid, "logout");
            }
        }
        return ApiResp.ok();
    }

    /** 当前用户信息（含商家子角色） */
    @GetMapping("/user/me")
    public ApiResp<Map<String, Object>> me(jakarta.servlet.http.HttpServletRequest req) {
        // 员工（子账号）走 t_merchant_staff，避免用 staffId 查 t_user 失败导致刷新即登出
        Long sid = TokenContext.getStaffId();
        if (sid != null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, nickname, role, merchant_id, status FROM t_merchant_staff WHERE id = ?", sid);
            if (rows.isEmpty()) throw new BizException(ErrorCode.PASSWORD_ERROR, "员工账号不存在");
            Map<String, Object> s = rows.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sid);
            m.put("nickname", s.get("nickname"));
            m.put("avatar", null);
            m.put("phone", "");
            m.put("vipLevel", 0);
            m.put("gender", 0);
            m.put("region", "");
            m.put("bio", "");
            m.put("verified", 0);
            m.put("role", "merchant");
            m.put("subRole", s.get("role"));
            m.put("merchantId", s.get("merchant_id"));
            m.put("staffId", sid);
            m.put("isStaff", true);
            return ApiResp.ok(m);
        }
        long uid = TokenContext.requireUserId();
        User u = userService.findById(uid);
        if (u == null) throw new BizException(ErrorCode.PASSWORD_ERROR, "用户不存在");
        Map<String, Object> m = UserService.toMap(u, req);
        // 角色以账号真实角色为准。历史实现误用 requireSubRole()（商家子角色，缺省 OWNER），
        // 导致所有普通用户刷新后都变成 role=OWNER，前端角色守卫完全失真。
        m.put("role", roleOf(uid));
        m.put("subRole", TokenContext.requireSubRole());
        m.put("merchantId", TokenContext.requireMerchantId());
        m.put("staffId", TokenContext.getStaffId());
        m.put("isStaff", TokenContext.getStaffId() != null);
        return ApiResp.ok(m);
    }

    /** 员工（子账号）登录：username + password，映射到所属商家 */
    private ApiResp<Map<String, Object>> staffLogin(Map<String, Object> body, HttpServletRequest req) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        if (username == null || password == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入员工账号和密码");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.id, s.merchant_id, s.password, s.nickname, s.role, s.status " +
            "FROM t_merchant_staff s WHERE s.username = ?", username);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PASSWORD_ERROR, "员工账号不存在");
        Map<String, Object> s = rows.get(0);
        if (s.get("status") != null && ((Number) s.get("status")).intValue() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED, "该员工账号已停用");
        }
        String hash = (String) s.get("password");
        if (!BCrypt.checkpw(password, hash)) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "密码错误");
        }
        long staffId = ((Number) s.get("id")).longValue();
        long merchantId = ((Number) s.get("merchant_id")).longValue();
        String subRole = (String) s.get("role");
        String token = jwtService.issue(staffId, "merchant", merchantId, subRole, staffId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", staffId);
        data.put("nickname", s.get("nickname"));
        data.put("avatar", null);
        data.put("vipLevel", 0);
        data.put("role", "merchant");
        data.put("subRole", subRole);
        data.put("merchantId", merchantId);
        data.put("staffId", staffId);
        data.put("isStaff", true);
        return ApiResp.ok(data);
    }

    /**
     * 读取账号角色。
     *
     * 角色以 t_user.role 为唯一依据（user / merchant / admin）。
     * 历史实现用手机号前缀推断（137→admin、139→merchant），在真实数据下会把大量普通用户
     * 误判为管理员/商家（例如 13712345678、13762522xxx 全部变成 admin），已废弃。
     */
    private String roleOf(long userId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role FROM t_user WHERE id = ? AND is_deleted = 0", userId);
            if (!rows.isEmpty()) {
                String r = (String) rows.get(0).get("role");
                if (r != null && !r.isBlank()) return r.toLowerCase();
            }
        } catch (Exception ignore) {}
        return "user";
    }
}