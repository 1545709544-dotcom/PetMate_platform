package com.petmate.security;

import com.petmate.common.ErrorCode;
import com.petmate.common.BizException;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 JwtAuthFilter 写入，由 Controller/Service 读取。
 */
public final class TokenContext {
    public enum Role { USER, MERCHANT, ADMIN }

    public static class Principal {
        public final long userId;
        public final String role;       // user / merchant / admin
        public final long merchantId;    // 拥有者商家 id（员工为其所属商家）
        public final String subRole;     // OWNER / OPERATOR / CS / WAREHOUSE
        public final Long staffId;       // 员工 id，店主为 null
        /** t_user.status 快照：0=正常 1=封禁 2=注销（-1 表示未注入） */
        public final int userStatus;
        public Principal(long userId, String role, long merchantId, String subRole, Long staffId) {
            this(userId, role, merchantId, subRole, staffId, -1);
        }
        public Principal(long userId, String role, long merchantId, String subRole, Long staffId, int userStatus) {
            this.userId = userId;
            this.role = role;
            this.merchantId = merchantId;
            this.subRole = subRole;
            this.staffId = staffId;
            this.userStatus = userStatus;
        }
        /** 店主构造（subRole=OWNER，staffId=null） */
        public static Principal owner(long userId, String role) {
            return new Principal(userId, role, userId, "OWNER", null);
        }
    }

    private static final ThreadLocal<Principal> CTX = new ThreadLocal<>();

    private TokenContext() {}

    public static void set(long userId, String role, long merchantId, String subRole, Long staffId) {
        CTX.set(new Principal(userId, role, merchantId, subRole, staffId));
    }
    public static void set(long userId, String role, long merchantId, String subRole, Long staffId, int userStatus) {
        CTX.set(new Principal(userId, role, merchantId, subRole, staffId, userStatus));
    }
    public static Principal get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }

    /**
     * 当前登录用户 id。
     *
     * 这里同时做「账号是否可用」的强制校验：JWT 有效期 7 天，若不在此处拦截，
     * 被封禁 / 注销的账号仍能拿旧 token 正常调用全部业务接口（历史缺陷：requireActive 零调用方）。
     * requireUserId 是所有需要登录态接口的唯一入口，放在这里即可全局生效。
     */
    public static long requireUserId() {
        Principal p = CTX.get();
        if (p == null) throw new BizException(ErrorCode.NOT_LOGIN);
        if (p.userStatus == 1) throw new BizException(ErrorCode.ACCOUNT_BANNED, "账号已被封禁，请联系客服");
        if (p.userStatus == 2) throw new BizException(ErrorCode.ACCOUNT_BANNED, "账号已注销");
        return p.userId;
    }

    /** 当前账号角色（user / merchant / admin），未登录返回 null */
    public static String currentRole() {
        Principal p = CTX.get();
        return p == null ? null : p.role;
    }

    /** 当前操作的商家 id（员工为所属商家，店主为自己的 id） */
    public static long requireMerchantId() {
        Principal p = CTX.get();
        if (p == null) throw new BizException(ErrorCode.NOT_LOGIN);
        return p.merchantId;
    }

    public static String requireSubRole() {
        Principal p = CTX.get();
        if (p == null) throw new BizException(ErrorCode.NOT_LOGIN);
        return p.subRole != null ? p.subRole : "OWNER";
    }

    /** 角色校验：不在允许列表内抛 NO_PERMISSION */
    public static void requireSubRole(String... allowed) {
        String cur = requireSubRole();
        for (String a : allowed) if (a.equalsIgnoreCase(cur)) return;
        throw new BizException(ErrorCode.NO_PERMISSION, "当前角色无权限执行此操作");
    }

    public static Long getStaffId() {
        Principal p = CTX.get();
        return p == null ? null : p.staffId;
    }

    public static void requireRole(String expected) {
        Principal p = CTX.get();
        if (p == null) throw new BizException(ErrorCode.NOT_LOGIN);
        if (!expected.equalsIgnoreCase(p.role)) throw new BizException(ErrorCode.NO_PERMISSION);
    }

    /**
     * 校验当前账号未被封禁/注销。
     * JwtAuthFilter 在写入上下文时已注入 userStatus 快照；调用本方法即可在不查 DB 的情况下
     * 拦截被封禁账号（status=1）或已注销账号（status=2）。
     * 注：未注入状态（userStatus=-1，例如登录态演示 token）则放行，由业务自行兜底。
     */
    public static void requireActive() {
        Principal p = CTX.get();
        if (p == null) throw new BizException(ErrorCode.NOT_LOGIN);
        if (p.userStatus == 1) throw new BizException(ErrorCode.ACCOUNT_BANNED, "账号已被封禁，请联系客服");
        if (p.userStatus == 2) throw new BizException(ErrorCode.ACCOUNT_BANNED, "账号已注销");
    }

    /**
     * 角色校验 + 启用校验：先校验角色，再校验账号是否被封禁/注销。
     * 用于：用户管理、商家管理、订单管理等所有需要登录态的接口。
     */
    public static void requireRoleAndActive(String expected) {
        requireRole(expected);
        requireActive();
    }
}
