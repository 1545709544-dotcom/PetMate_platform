package com.petmate.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析
 *
 * v2.0: token 增加 jti（UUID）claim，用于黑名单撤销。
 */
@Component
public class JwtService {
    private final SecretKey key;
    private final long expireSeconds;

    public JwtService(@Value("${petmate.jwt.secret}") String secret,
                      @Value("${petmate.jwt.expire-seconds}") long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public String issue(long userId, String role) {
        // 兼容旧调用：默认店主身份
        return issue(userId, role, userId, "OWNER", null);
    }

    public String issue(long userId, String role, long merchantId, String subRole, Long staffId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000L);
        var b = Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key);
        if (merchantId > 0) b.claim("mid", merchantId);
        if (subRole != null) b.claim("srole", subRole);
        if (staffId != null) b.claim("sid", staffId);
        return b.compact();
    }

    /** 解析 token,失败返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}