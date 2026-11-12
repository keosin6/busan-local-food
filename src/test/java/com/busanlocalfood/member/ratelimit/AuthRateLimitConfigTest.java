package com.busanlocalfood.member.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 기동 시 설정 검증. HMAC 키 누락, 형식 오류, 길이 부족, JWT 키와 동일한 값은 기동을 막는다. */
class AuthRateLimitConfigTest {

    /** LockRetryTemplate 빈이 트랜잭션 관리자를 요구한다. 이 테스트는 설정 검증만 보므로 목으로 채운다. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthRateLimitConfig.class)
            .withBean(org.springframework.transaction.PlatformTransactionManager.class,
                    () -> org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));

    private static String randomBase64(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void HMAC_키가_없으면_기동이_실패한다() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void HMAC_키가_Base64가_아니거나_짧으면_기동이_실패한다() {
        runner.withPropertyValues("auth.rate-limit.hmac-secret=%%%").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("auth.rate-limit.hmac-secret=" + randomBase64(16))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 올바른_HMAC_키이면_해셔를_만든다() {
        runner.withPropertyValues("auth.rate-limit.hmac-secret=" + randomBase64(32))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(AuthRateLimitHasher.class);
                    assertThat(ctx).hasSingleBean(ClientIpResolver.class);
                });
    }

    @Test
    void JWT_서명_키와_같은_값이면_기동이_실패한다() {
        String secret = randomBase64(32);
        SecretKey jwtKey = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");

        runner.withPropertyValues("auth.rate-limit.hmac-secret=" + secret)
                .withBean("jwtSigningKey", SecretKey.class, () -> jwtKey)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void JWT_서명_키와_다른_값이면_기동한다() {
        SecretKey jwtKey = new SecretKeySpec(Base64.getDecoder().decode(randomBase64(32)), "HmacSHA256");

        runner.withPropertyValues("auth.rate-limit.hmac-secret=" + randomBase64(32))
                .withBean("jwtSigningKey", SecretKey.class, () -> jwtKey)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void 신뢰_프록시_설정에_잘못된_IP가_있으면_기동이_실패한다() {
        runner.withPropertyValues("auth.rate-limit.hmac-secret=" + randomBase64(32),
                        "app.security.trusted-proxies[0]=999.1.1.1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
