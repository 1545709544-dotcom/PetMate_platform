package com.petmate.security;

import com.petmate.common.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT 鉴权过滤器 —— 解析 Authorization 头,设置当前用户上下文;同时生成 traceId。
 * v7: 注入 t_user.status 快照到 Principal，供 TokenContext.requireActive 拦截封禁/注销。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final JdbcTemplate jdbc;

    public JwtAuthFilter(JwtService jwtService, TokenBlacklist blacklist, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. traceId
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        TraceContext.set(traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            // 2. JWT
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                Claims c = jwtService.parse(token);
                    if (c != null) {
                        // 黑名单校验：被撤销的 token 不再放行（业务层会抛 NOT_LOGIN）
                        String jti = c.getId();
                        if (jti != null && blacklist.isRevoked(jti)) {
                            // 跳过 TokenContext.set，等同"未登录"
                        } else {
                            long uid = Long.parseLong(c.getSubject());
                            String role = c.get("role", String.class);
                            if (role == null) role = "user";
                            role = role.toLowerCase();
                            Long mid = c.get("mid", Long.class);
                            if (mid == null) mid = uid;
                            String sub = c.get("srole", String.class);
                            if (sub == null) sub = "OWNER";
                            Long sid = c.get("sid", Long.class);
                            // v17: 状态注入分两路——admin/merchant-店主走 t_user.status；merchant-员工走 t_merchant_staff.status
                            //   语义：t_user.status = 0 正常 / 1 封禁 / 2 注销
                            //         t_merchant_staff.status = 1 启用 / 0 停用
                            // 为兼容 Principal.userStatus 的 0/1/2 语义，员工 status=0(停用) → userStatus=1(封禁)
                            int userStatus = -1;
                            try {
                                if ("merchant".equals(role) && sid != null) {
                                    // 员工 token：principal.userId = staffId，用 merchant_id 取所属店主 userId 查 status 也无意义，
                                    // 直接查 staff 表的 status 列。
                                    Integer st = jdbc.queryForObject(
                                        "SELECT status FROM t_merchant_staff WHERE id = ?",
                                        Integer.class, sid);
                                    if (st != null) {
                                        // 员工 status: 1=启用, 0=停用 → 映射为 userStatus 0=正常, 1=封禁
                                        userStatus = (st == 0) ? 1 : 0;
                                    }
                                } else {
                                    Integer st = jdbc.queryForObject(
                                        "SELECT status FROM t_user WHERE id = ? AND is_deleted = 0",
                                        Integer.class, uid);
                                    if (st != null) userStatus = st;
                                }
                            } catch (Exception ignore) {
                                // 用户不存在/已删，不写入状态，后续 requireActive 会放行
                            }
                            TokenContext.set(uid, role, mid, sub, sid, userStatus);
                            // v17: 全员封禁检查（管理员封禁账号会写入 jti='user:{id}'）
                            if (blacklist.userRevoked(uid)) {
                                TokenContext.clear();
                            }
                        }
                    }
            }
            chain.doFilter(request, response);
        } finally {
            TokenContext.clear();
            TraceContext.clear();
        }
    }
}
