package com.petmate.user;

import com.petmate.common.BizException;
import com.petmate.common.CityUtil;
import com.petmate.common.ErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    /** 演示账号统一密码 123456;种子 SQL 中已写死 BCrypt 哈希,不做 NULL 兜底 */
    static final String DEMO_PASSWORD = "123456";

    private final JdbcTemplate jdbc;
    private final com.petmate.common.NotifyService notify;
    private final RowMapper<User> MAPPER = this::mapRow;

    public UserService(JdbcTemplate jdbc, com.petmate.common.NotifyService notify) {
        this.jdbc = jdbc;
        this.notify = notify;
    }

    public User findById(long id) {
        try {
            return jdbc.queryForObject(
                "SELECT id, phone, nickname, avatar, gender, birthday, vip_level, vip_expire_at, bio, status, " +
                "password, last_login_at, created_at, open_id, real_name, id_card, verified, region, city FROM t_user WHERE id = ? AND is_deleted = 0",
                MAPPER, id);
        } catch (EmptyResultDataAccessException e) { return null; }
    }

    public User findByPhone(String phone) {
        try {
            return jdbc.queryForObject(
                "SELECT id, phone, nickname, avatar, gender, birthday, vip_level, vip_expire_at, bio, status, " +
                "password, last_login_at, created_at, open_id, real_name, id_card, verified, region, city FROM t_user WHERE phone = ? AND is_deleted = 0",
                MAPPER, phone);
        } catch (EmptyResultDataAccessException e) { return null; }
    }

    public User createByPhone(String phone) {
        // 演示场景:首次登录手机号自动注册
        String avatar = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + phone;
        jdbc.update(
            "INSERT INTO t_user (phone, nickname, avatar, vip_level, status) VALUES (?, ?, ?, 0, 0)",
            phone, "萌宠家长" + phone.substring(Math.max(0, phone.length() - 4)), avatar);
        return findByPhone(phone);
    }

    /**
     * 显式注册：手机号唯一,昵称自定义,密码用 BCrypt 写入。
     * 若手机号已存在,抛 PHONE_REGISTERED。
     */
    public User register(String phone, String nickname, String rawPassword) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "昵称不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 32) {
            throw new BizException(ErrorCode.PARAM_ERROR, "密码长度需 6-32 位");
        }
        if (findByPhone(phone) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该手机号已注册");
        }
        String avatar = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + phone;
        String hashed = BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
        jdbc.update(
            "INSERT INTO t_user (phone, nickname, avatar, password, vip_level, status) " +
            "VALUES (?, ?, ?, ?, 0, 0)",
            phone, nickname.trim(), avatar, hashed);
        return findByPhone(phone);
    }

    /**
     * 开通 / 续费 / 升级 会员，并返回最新 VIP 信息。
     * - cur == 0 -> 开通所选档位，有效期 1 年
     * - cur > 0 && plan > cur -> 升级，重置有效期 1 年
     * - cur > 0 && plan == cur -> 同档续费，在已有到期日基础上延长 1 年（已过期则从今天起算）
     * - cur > 0 && plan < cur -> 拒绝降级（BizException）
     *
     * @param uid    用户 ID
     * @param target 目标会员档位 (1=白银/2=黄金/3=钻石)
     * @return { action, vipLevel, vip, vipExpireAt }
     */
    public Map<String, Object> grantVip(long uid, int target) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT vip_level, vip_expire_at FROM t_user WHERE id = ? AND is_deleted = 0", uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");
        Map<String, Object> r = rows.get(0);
        int cur = r.get("vip_level") == null ? 0 : ((Number) r.get("vip_level")).intValue();
        Object veObj = r.get("vip_expire_at");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = null;
        if (veObj instanceof java.sql.Timestamp ts) expire = ts.toLocalDateTime();
        else if (veObj instanceof LocalDateTime ldt) expire = ldt;

        // 已开通：不降级（保持当前等级与有效期，订单仍视为成功，避免支付成功却卡在待开通）
        if (cur > 0 && target < cur) {
            Map<String, Object> keep = new LinkedHashMap<>();
            keep.put("action", "keep");
            keep.put("vipLevel", cur);
            keep.put("vip", true);
            keep.put("vipExpireAt", expire == null ? null : expire.toString());
            return keep;
        }

        String action;
        LocalDateTime newExpire;
        if (cur <= 0) {
            // 首次开通
            action = "open";
            newExpire = now.plusYears(1);
            jdbc.update("UPDATE t_user SET vip_level = ?, vip_expire_at = ? WHERE id = ?",
                target, java.sql.Timestamp.valueOf(newExpire), uid);
        } else if (target > cur) {
            // 升级：等级提升 + 重置有效期 1 年（从今天起）
            action = "upgrade";
            newExpire = now.plusYears(1);
            jdbc.update("UPDATE t_user SET vip_level = ?, vip_expire_at = ? WHERE id = ?",
                target, java.sql.Timestamp.valueOf(newExpire), uid);
        } else {
            // 同档续费：在已有到期日基础上延长 1 年（已过期则从今天起算）
            LocalDateTime base = (expire != null && expire.isAfter(now)) ? expire : now;
            action = "renew";
            newExpire = base.plusYears(1);
            jdbc.update("UPDATE t_user SET vip_expire_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(newExpire), uid);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("vipLevel", target);
        out.put("vip", true);
        out.put("vipExpireAt", newExpire.toString());

        // 会员变动通知用户
        String levelName = vipLevelName(target);
        String title;
        String content;
        if ("open".equals(action)) {
            title = "会员开通成功";
            content = "恭喜您已开通 PetMate " + levelName + "会员，有效期至 " + newExpire.toLocalDate();
        } else if ("upgrade".equals(action)) {
            title = "会员升级成功";
            content = "您已升级为 " + levelName + "会员，有效期至 " + newExpire.toLocalDate();
        } else {
            title = "会员续费成功";
            content = "您的会员已续费，新有效期至 " + newExpire.toLocalDate();
        }
        notify.notifyUser(uid, "vip", title, content, null);
        return out;
    }

    /** VIP 档位中文名（0=普通/1=白银/2=黄金） */
    private static String vipLevelName(int level) {
        return switch (level) {
            case 1 -> "白银";
            case 2 -> "黄金";
            case 3 -> "钻石";
            default -> "普通";
        };
    }

    public boolean checkPassword(User user, String raw) {
        if (raw == null) return false;
        String hashed = user.getPassword();
        if (hashed == null || hashed.isEmpty()) {
            // 兼容老库：seed 未补齐 password 的兜底。生产/演示库应跑过 sql/seed.sql 的
            // 幂等 UPDATE 把 password 写为 BCrypt("123456") 哈希,走下面真实校验分支。
            return DEMO_PASSWORD.equals(raw);
        }
        try {
            return BCrypt.checkpw(raw, hashed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 修改密码：校验旧密码 → BCrypt 重写新密码
     * @return true 成功
     * @throws BizException PASSWORD_ERROR（旧的错）/ PARAM_ERROR（参数缺失或新旧密码相同）
     */
    public boolean changePassword(long userId, String oldRaw, String newRaw) {
        if (oldRaw == null || oldRaw.isEmpty() || newRaw == null || newRaw.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "旧密码/新密码必填");
        }
        if (newRaw.length() < 6) {
            throw new BizException(ErrorCode.PARAM_ERROR, "新密码至少 6 位");
        }
        if (oldRaw.equals(newRaw)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "新密码不能与旧密码相同");
        }
        User u;
        try {
            u = jdbc.queryForObject(
                "SELECT id, phone, nickname, password FROM t_user WHERE id = ? AND is_deleted = 0",
                (rs, i) -> {
                    User x = new User();
                    x.setId(rs.getLong("id"));
                    x.setPhone(rs.getString("phone"));
                    x.setNickname(rs.getString("nickname"));
                    x.setPassword(rs.getString("password"));
                    return x;
                }, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");
        }
        if (!checkPassword(u, oldRaw)) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "旧密码错误");
        }
        String hashed = BCrypt.hashpw(newRaw, BCrypt.gensalt(10));
        jdbc.update("UPDATE t_user SET password = ? WHERE id = ?", hashed, userId);
        return true;
    }

    public void markLogin(long userId) {
        jdbc.update("UPDATE t_user SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), userId);
    }

    public void updateProfile(long userId, Map<String, Object> patch) {
        if (patch.containsKey("nickname")) jdbc.update("UPDATE t_user SET nickname = ? WHERE id = ?", patch.get("nickname"), userId);
        if (patch.containsKey("avatar")) {
            Object av = patch.get("avatar");
            if (av != null && !av.toString().isEmpty()) {
                String newAvatar = av.toString();
                jdbc.update("UPDATE t_user SET avatar = ? WHERE id = ?", newAvatar, userId);
                // 同步回写该用户帖子/评论里的头像快照，避免社区流头像不随更换而更新
                jdbc.update("UPDATE t_post SET user_avatar = ? WHERE user_id = ? AND is_deleted = 0", newAvatar, userId);
                jdbc.update("UPDATE t_post_comment SET user_avatar = ? WHERE user_id = ?", newAvatar, userId);
                jdbc.update("UPDATE t_article_comment SET user_avatar = ? WHERE user_id = ?", newAvatar, userId);
            }
        }
        if (patch.containsKey("gender"))   jdbc.update("UPDATE t_user SET gender = ? WHERE id = ?", patch.get("gender"), userId);
        if (patch.containsKey("bio"))      jdbc.update("UPDATE t_user SET bio = ? WHERE id = ?", patch.get("bio"), userId);
        if (patch.containsKey("birthday")) jdbc.update("UPDATE t_user SET birthday = ? WHERE id = ?", patch.get("birthday"), userId);
        if (patch.containsKey("region"))   jdbc.update("UPDATE t_user SET region = ? WHERE id = ?", patch.get("region"), userId);
        // city 是社区「同城」的匹配键，必须归一化后落库（见 CityUtil）。
        // 前端只传了 region 没传 city 时，服务端从 region 反推，避免同城功能因少传字段而失效。
        String cityRaw = null;
        if (patch.containsKey("city")) {
            cityRaw = patch.get("city") == null ? null : String.valueOf(patch.get("city"));
        } else if (patch.containsKey("region")) {
            cityRaw = patch.get("region") == null ? null : String.valueOf(patch.get("region"));
        }
        if (patch.containsKey("city") || patch.containsKey("region")) {
            jdbc.update("UPDATE t_user SET city = ? WHERE id = ?", CityUtil.normalize(cityRaw), userId);
        }
    }

    /**
     * 实名认证：校验身份证(18位) + 姓名,置 real_name/id_card/verified=1。
     * 已认证账号不允许重复提交。
     */
    public void realname(long uid, String realName, String idCard) {
        if (realName == null || realName.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入真实姓名");
        }
        if (idCard == null || !idCard.matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "身份证号格式不正确(需18位)");
        }
        List<Map<String, Object>> cur = jdbc.queryForList(
            "SELECT verified FROM t_user WHERE id = ? AND is_deleted = 0", uid);
        if (!cur.isEmpty()) {
            Object v = cur.get(0).get("verified");
            int ver = v == null ? 0 : ((Number) v).intValue();
            if (ver == 1) throw new BizException(ErrorCode.PARAM_ERROR, "该账号已完成实名认证");
        }
        jdbc.update(
            "UPDATE t_user SET real_name = ?, id_card = ?, verified = 1 WHERE id = ? AND is_deleted = 0",
            realName.trim(), idCard.trim().toUpperCase(), uid);
    }

    /**
     * 注销账号：校验登录密码后软删除(is_deleted=1)。
     * 发布态下前端拿到 401/登出态即跳转登录页,无需在本方法内处理 token 失效。
     */
    public void deleteAccount(long uid, String rawPassword) {
        User u = jdbc.queryForObject(
            "SELECT id, password FROM t_user WHERE id = ? AND is_deleted = 0",
            (rs, i) -> {
                User x = new User();
                x.setId(rs.getLong("id"));
                x.setPassword(rs.getString("password"));
                return x;
            }, uid);
        if (u == null) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");
        if (!checkPassword(u, rawPassword)) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "密码错误");
        }
        jdbc.update("UPDATE t_user SET is_deleted = 1 WHERE id = ?", uid);
    }

    public static Map<String, Object> toMap(User u) {
        return toMap(u, null);
    }

    /**
     * 输出用户信息时,把 DB 里存的相对路径头像（如 /static/uploads/avatar/xxx.jpg）
     * 转成绝对 URL,避免前端跨域/部署差异导致图片加载不到。
     * 已经是 http(s):// 开头或 data: URI 时保持不变。
     */
    public static Map<String, Object> toMap(User u, jakarta.servlet.http.HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatar", toAbsoluteAvatar(req, u.getAvatar()));
        m.put("phone", u.getPhone());
        m.put("vipLevel", u.getVipLevel());
        m.put("vipExpireAt", u.getVipExpireAt() == null ? null : u.getVipExpireAt().toString());
        m.put("gender", u.getGender());
        m.put("birthday", u.getBirthday() == null ? null : u.getBirthday().toString());
        m.put("bio", u.getBio());
        m.put("verified", u.getVerified() == null ? 0 : u.getVerified());
        m.put("realName", u.getRealName());
        m.put("region", u.getRegion());
        m.put("city", u.getCity());
        return m;
    }

    public static String toAbsoluteAvatar(jakarta.servlet.http.HttpServletRequest req, String avatar) {
        if (avatar == null || avatar.isEmpty()) return avatar;
        if (avatar.startsWith("http://") || avatar.startsWith("https://") || avatar.startsWith("data:")) {
            return avatar;
        }
        // 本服务上传的静态资源：DB 里可能存的是老格式 /static/... 或新格式 /api/v1/static/...
        // 都规范化为带 context-path 的 URL,否则前端拿到不带前缀的路径会绕开 DispatcherServlet
        if (req == null || !avatar.startsWith("/static/")) return avatar;
        String ctx = req.getContextPath(); // = /api/v1
        String withCtx = ctx + avatar;     // /api/v1/static/uploads/avatar/xxx.jpg
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return defaultPort
                ? scheme + "://" + host + withCtx
                : scheme + "://" + host + ":" + port + withCtx;
    }

    private User mapRow(ResultSet rs, int i) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setPhone(rs.getString("phone"));
        u.setNickname(rs.getString("nickname"));
        u.setAvatar(rs.getString("avatar"));
        u.setGender(rs.getInt("gender"));
        java.sql.Date bd = rs.getDate("birthday");
        if (bd != null) u.setBirthday(bd.toLocalDate());
        u.setVipLevel(rs.getInt("vip_level"));
        java.sql.Timestamp ve = rs.getTimestamp("vip_expire_at");
        if (ve != null) u.setVipExpireAt(ve.toLocalDateTime());
        u.setBio(rs.getString("bio"));
        u.setStatus(rs.getInt("status"));
        u.setPassword(rs.getString("password"));
        java.sql.Timestamp ll = rs.getTimestamp("last_login_at");
        if (ll != null) u.setLastLoginAt(ll.toLocalDateTime());
        java.sql.Timestamp cr = rs.getTimestamp("created_at");
        if (cr != null) u.setCreatedAt(cr.toLocalDateTime());
        u.setOpenId(rs.getString("open_id"));
        u.setRealName(rs.getString("real_name"));
        u.setIdCard(rs.getString("id_card"));
        int verified = rs.getInt("verified");
        u.setVerified(rs.wasNull() ? 0 : verified);
        u.setRegion(rs.getString("region"));
        u.setCity(rs.getString("city"));
        return u;
    }
}
