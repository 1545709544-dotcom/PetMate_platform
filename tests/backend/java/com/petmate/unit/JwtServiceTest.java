package com.petmate.unit;

import com.petmate.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 · JWT 签发与解析。
 *
 * <p>被测：{@code com.petmate.security.JwtService}
 *
 * <p>本项目<b>没有 Spring Security 鉴权链</b>，鉴权 100% 依赖此类解出的 claims
 * 加上各 Controller 手写的 {@code TokenContext.requireXxx()}。
 * 因此 JwtService 是整个权限体系的地基 —— 验签一旦失效（比如篡改的 token 被解出内容），
 * 全站鉴权同时失守。这组用例锁死「篡改必须解析失败」这条底线。
 *
 * <p>不依赖 Spring 上下文：直接 new，密钥用测试常量。
 */
@Tag("unit")
@DisplayName("L1 · JWT 服务 JwtService")
class JwtServiceTest {

    /** 至少 32 字节，HS256 要求。 */
    private static final String SECRET = "test-only-secret-fixed-for-junit-must-be-at-least-32-bytes-long";
    private static final long EXPIRE_SECONDS = 604800L; // 7 天，与生产一致

    private JwtService jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET, EXPIRE_SECONDS);
    }

    @Nested
    @DisplayName("签发与解析往返")
    class RoundTrip {

        @Test
        void 普通用户_token_往返后_sub_与_role_保持一致() {
            String token = jwt.issue(1001L, "user", 0, null, null);
            Claims claims = jwt.parse(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("1001");
            assertThat(claims.get("role")).isEqualTo("user");
        }

        @Test
        void 商家_token_携带_mid_与_srole() {
            String token = jwt.issue(2001L, "merchant", 30001L, "OWNER", null);
            Claims claims = jwt.parse(token);

            assertThat(claims.getSubject()).isEqualTo("2001");
            assertThat(claims.get("role")).isEqualTo("merchant");
            assertThat(((Number) claims.get("mid")).longValue()).isEqualTo(30001L);
            assertThat(claims.get("srole")).isEqualTo("OWNER");
        }

        @Test
        void 员工_token_携带_sid() {
            String token = jwt.issue(5001L, "merchant", 30001L, "CS", 5001L);
            Claims claims = jwt.parse(token);

            assertThat(((Number) claims.get("sid")).longValue()).isEqualTo(5001L);
            assertThat(claims.get("srole")).isEqualTo("CS");
        }

        @Test
        void 每个_token_都有唯一_jti_用于黑名单撤销() {
            String t1 = jwt.issue(1001L, "user");
            String t2 = jwt.issue(1001L, "user");

            String jti1 = jwt.parse(t1).getId();
            String jti2 = jwt.parse(t2).getId();

            assertThat(jti1).isNotBlank();
            // 同一用户两次签发的 jti 必须不同，否则登出一个会误伤另一个
            assertThat(jti1).isNotEqualTo(jti2);
        }

        @Test
        void staffId_为_null_时不写入_sid_claim() {
            String token = jwt.issue(1001L, "user", 0, null, null);
            assertThat(jwt.parse(token).get("sid")).isNull();
        }

        @Test
        void merchantId_为_0_时不写入_mid_claim() {
            String token = jwt.issue(1001L, "user", 0, "OWNER", null);
            assertThat(jwt.parse(token).get("mid")).isNull();
        }

        @Test
        void 兼容旧签名的两参重载_默认为店主身份() {
            String token = jwt.issue(2001L, "merchant");
            Claims claims = jwt.parse(token);

            // 旧调用约定：merchantId 默认取 userId，subRole 默认 OWNER
            assertThat(((Number) claims.get("mid")).longValue()).isEqualTo(2001L);
            assertThat(claims.get("srole")).isEqualTo("OWNER");
        }
    }

    @Nested
    @DisplayName("安全边界 —— 全站鉴权的底线")
    class SecurityBoundary {

        @Test
        void 篡改签名的_token_解析失败() {
            String token = jwt.issue(1001L, "user");
            int lastDot = token.lastIndexOf('.');
            String sig = token.substring(lastDot + 1);
            char last = sig.charAt(sig.length() - 1);
            char flipped = (last == 'A') ? 'B' : 'A';
            String tampered = token.substring(0, lastDot + 1)
                    + sig.substring(0, sig.length() - 1) + flipped;

            assertThat(jwt.parse(tampered))
                    .as("篡改签名的 token 必须解析失败，否则全站鉴权失守")
                    .isNull();
        }

        @Test
        void 篡改_payload_提权为_admin_后解析失败() {
            // 攻击场景：把 role=user 改成 role=admin 再重新 base64 编码
            String token = jwt.issue(1001L, "user");
            String[] parts = token.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"1001\",\"role\":\"admin\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String forged = parts[0] + "." + forgedPayload + "." + parts[2];

            assertThat(jwt.parse(forged))
                    .as("篡改 payload 提权必须被验签拦截")
                    .isNull();
        }

        @Test
        void 用错误密钥签发的_token_解析失败() {
            JwtService attacker = new JwtService(
                    "attacker-guessed-a-completely-different-secret-key-32b", EXPIRE_SECONDS);
            String forged = attacker.issue(1L, "admin");

            assertThat(jwt.parse(forged))
                    .as("他人密钥签发的 token 不能被本服务接受")
                    .isNull();
        }

        @Test
        void 已过期的_token_解析失败() throws InterruptedException {
            // 有效期 0 秒 → 签发即过期
            JwtService shortLived = new JwtService(SECRET, 0L);
            String token = shortLived.issue(1001L, "user");
            Thread.sleep(1100); // 跨过 jjwt 的秒级时钟容差

            assertThat(shortLived.parse(token)).isNull();
        }

        @Test
        void 格式非法的_token_解析失败且不抛异常() {
            // parse() 契约是「失败返回 null」，任何输入都不应把异常抛给调用方
            assertThat(jwt.parse("not-a-jwt")).isNull();
            assertThat(jwt.parse("")).isNull();
            assertThat(jwt.parse("a.b.c")).isNull();
            assertThat(jwt.parse(null)).isNull();
        }

        @Test
        void 无签名的_none_算法_token_被拒绝() {
            // CVE 经典攻击：alg 改成 none 试图跳过验签
            String header = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"1\",\"role\":\"admin\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThat(jwt.parse(header + "." + payload + "."))
                    .as("alg=none 的 token 必须被拒绝")
                    .isNull();
        }
    }
}
