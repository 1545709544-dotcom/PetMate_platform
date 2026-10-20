package com.petmate.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单（基于 jti）
 *
 * v17：持久化到 t_token_blacklist（Caffeine 缓存 + DB 回源）。
 *
 * 适用场景：
 *  - 用户登出后强制下线（/auth/logout 写入）
 *  - 账号被封禁后主动踢人（管理员接口 revokeByUser）
 *  - 安全事件全局撤销（管理后台触发）
 *
 * 性能特征：
 *  - isRevoked 先查 Caffeine（30s TTL），未命中查 DB
 *  - revoke 写 DB + 失效 Caffeine 条目
 *  - @Scheduled 每 30 分钟删过期条目（DB 与 Caffeine 都清）
 */
@Component
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);

    private final JdbcTemplate jdbc;

    /**
     * Caffeine 缓存：jti -> true（命中即放行）。
     * 仅用于"已被撤销"的 jti；未撤销的不缓存以减少内存占用。
     * TTL 30s 兼顾一致性（DB 是真源）与性能。
     */
    private final Cache<String, Boolean> revokedCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    public TokenBlacklist(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 将 jti 加入黑名单（持久化）。
     * @param jti          JWT 中的 jti claim
     * @param expEpochSec  该 token 的过期时间（epoch 秒），用于过期清理
     * @param userId       token 所属用户 id（便于按用户批量撤销）
     * @param reason       logout / ban / security（可空）
     */
    public void revoke(String jti, long expEpochSec, long userId, String reason) {
        if (jti == null || jti.isEmpty()) return;
        Timestamp expAt = new Timestamp(expEpochSec * 1000L);
        try {
            jdbc.update(
                "INSERT IGNORE INTO t_token_blacklist (jti, user_id, exp_at, reason) VALUES (?, ?, ?, ?)",
                jti, userId, expAt, reason);
        } catch (Exception e) {
            log.warn("[token-blacklist] revoke failed jti={}: {}", jti, e.getMessage());
        }
        revokedCache.put(jti, Boolean.TRUE);
    }

    /** 兼容旧调用：仅注销单个 jti（reason=logout, userId=0） */
    public void revoke(String jti, long expEpochSec) {
        revoke(jti, expEpochSec, 0L, "logout");
    }

    /**
     * 检查 jti 是否在黑名单中。命中且未过期返回 true。
     * 查找顺序：Caffeine → DB。DB 命中会回填 Caffeine。
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) return false;
        Boolean cached = revokedCache.getIfPresent(jti);
        if (cached != null) return Boolean.TRUE.equals(cached);

        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_token_blacklist WHERE jti = ? AND exp_at > NOW()",
                Integer.class, jti);
            boolean revoked = cnt != null && cnt > 0;
            if (revoked) revokedCache.put(jti, Boolean.TRUE);
            return revoked;
        } catch (Exception e) {
            log.warn("[token-blacklist] isRevoked failed jti={}: {}", jti, e.getMessage());
            // DB 异常时 fail-open（不阻断用户），与原内存版语义保持一致
            return false;
        }
    }

    /**
     * 按 userId 撤销该用户所有未过期 token（管理员封禁账号时调用）。
     * @return 实际写入的 jti 数（用于审计）
     */
    public int revokeByUser(long userId, String reason) {
        // 由于现有 token 不在 t_token_blacklist 表里，批量撤销只能在运行时
        // 通过维护 userId -> revoked 集合来覆盖。这里采用"未来派"方案：
        // 写一条特殊记录（jti='user:'+userId），JwtAuthFilter 在解析完 uid 后
        // 额外调用一次 userRevoked(userId) 即可生效（见 JwtAuthFilter.userRevoked）。
        String fakeJti = "user:" + userId;
        Timestamp farFuture = Timestamp.from(Instant.now().plus(Duration.ofDays(365 * 10)));
        try {
            jdbc.update(
                "INSERT IGNORE INTO t_token_blacklist (jti, user_id, exp_at, reason) VALUES (?, ?, ?, ?)",
                fakeJti, userId, farFuture, reason);
            revokedCache.put(fakeJti, Boolean.TRUE);
            return 1;
        } catch (Exception e) {
            log.warn("[token-blacklist] revokeByUser failed userId={}: {}", userId, e.getMessage());
            return 0;
        }
    }

    /** 检查某 userId 是否被批量撤销（管理员封禁时使用） */
    public boolean userRevoked(long userId) {
        String fakeJti = "user:" + userId;
        return isRevoked(fakeJti);
    }

    /** 每 30 分钟清理一次过期条目 */
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void cleanup() {
        try {
            int n = jdbc.update("DELETE FROM t_token_blacklist WHERE exp_at < NOW()");
            if (n > 0) log.info("[token-blacklist] cleanup removed {} expired entries", n);
            revokedCache.invalidateAll();
        } catch (Exception e) {
            log.warn("[token-blacklist] cleanup failed: {}", e.getMessage());
        }
    }

    /** 当前黑名单大小（监控/调试用） */
    public int size() {
        try {
            Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_token_blacklist", Integer.class);
            return cnt == null ? 0 : cnt;
        } catch (Exception e) {
            return 0;
        }
    }
}