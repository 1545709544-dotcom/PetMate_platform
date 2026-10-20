package com.petmate.support;

import com.petmate.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 各角色 token 签发工厂 —— 越权矩阵测试的基础设施。
 *
 * <p>直接复用生产的 {@link JwtService}（而非自己拼 JWT），保证测试用的 token
 * 与真实登录签发的完全同构：claims 结构、签名算法、过期策略都一致。
 * 密钥来自 {@code application-test.yml} 的固定测试密钥。
 *
 * <p>越权矩阵需要的身份维度：
 * <pre>
 *   匿名 / 用户A / 用户B / 商家A / 商家B / 员工(CS) / 员工(WAREHOUSE) / 管理员
 * </pre>
 * 「用户A vs 用户B」用于验证归属校验（IDOR），
 * 「商家A vs 商家B」用于验证店铺数据隔离，
 * 「员工子角色」用于验证 subRole 粒度的权限（关联后端计划 B-Z5）。
 */
@Component
public class TokenFactory {

    @Autowired
    private JwtService jwtService;

    // ---------- 角色常量（与生产代码 TokenContext 中的取值保持一致） ----------
    public static final String ROLE_USER     = "user";
    public static final String ROLE_MERCHANT = "merchant";
    public static final String ROLE_ADMIN    = "admin";

    public static final String SUB_OWNER     = "OWNER";
    public static final String SUB_OPERATOR  = "OPERATOR";
    public static final String SUB_CS        = "CS";
    public static final String SUB_WAREHOUSE = "WAREHOUSE";

    /** 普通用户 token。 */
    public String user(long userId) {
        return jwtService.issue(userId, ROLE_USER, 0, null, null);
    }

    /** 管理员 token。 */
    public String admin(long userId) {
        return jwtService.issue(userId, ROLE_ADMIN, 0, null, null);
    }

    /** 商家店主 token（merchantId 即店铺 id）。 */
    public String merchantOwner(long userId, long merchantId) {
        return jwtService.issue(userId, ROLE_MERCHANT, merchantId, SUB_OWNER, null);
    }

    /**
     * 商家员工 token。
     *
     * <p>注意：员工 token 的 {@code sub} 是 staffId 而非 userId ——
     * 这正是后端计划 B-A6 描述的风险点（查 t_user 必然查不到 → userStatus=-1 → 放行）。
     * 此处如实还原生产签发逻辑，便于用例复现该缺陷。
     */
    public String merchantStaff(long staffId, long merchantId, String subRole) {
        return jwtService.issue(staffId, ROLE_MERCHANT, merchantId, subRole, staffId);
    }

    /** 拼成可直接塞进 Authorization 头的值。 */
    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 篡改签名的 token —— 用于验证验签是否真实生效。 */
    public static String tampered(String token) {
        int lastDot = token.lastIndexOf('.');
        String sig = token.substring(lastDot + 1);
        // 翻转签名最后一个字符，构造签名不匹配但结构合法的 token
        char last = sig.charAt(sig.length() - 1);
        char flipped = (last == 'A') ? 'B' : 'A';
        return token.substring(0, lastDot + 1) + sig.substring(0, sig.length() - 1) + flipped;
    }
}
