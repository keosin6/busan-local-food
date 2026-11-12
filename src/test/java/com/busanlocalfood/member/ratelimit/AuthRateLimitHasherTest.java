package com.busanlocalfood.member.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AuthRateLimitHasherTest {

    private static String randomBase64(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static AuthRateLimitHasher hasher() {
        return new AuthRateLimitHasher(AuthRateLimitHasher.keyFromBase64(randomBase64(32)));
    }

    @Test
    void HMAC은_같은_입력에_같은_값이고_소문자_hex_64자이다() {
        AuthRateLimitHasher hasher = hasher();

        String first = hasher.hmac("client@example.com\n203.0.113.9");
        String second = hasher.hmac("client@example.com\n203.0.113.9");

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
    }

    @Test
    void HMAC은_입력이나_키가_다르면_다른_값이다() {
        AuthRateLimitHasher hasher = hasher();
        AuthRateLimitHasher otherKey = hasher();

        assertThat(hasher.hmac("203.0.113.9")).isNotEqualTo(hasher.hmac("203.0.113.10"));
        assertThat(hasher.hmac("203.0.113.9")).isNotEqualTo(otherKey.hmac("203.0.113.9"));
    }

    @Test
    void HMAC_결과는_원문_입력을_포함하지_않는다() {
        String ip = "203.0.113.9";

        assertThat(hasher().hmac(ip)).doesNotContain(ip);
    }

    @Test
    void 카운터_키는_접두어와_식별값을_이은_문자열의_SHA_256이다() throws Exception {
        AuthRateLimitHasher hasher = hasher();
        String hmac = hasher.hmac("203.0.113.9");

        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(("LOGIN_IP:" + hmac).getBytes(StandardCharsets.UTF_8));
        String hex = java.util.HexFormat.of().formatHex(expected);

        assertThat(hasher.counterKey("LOGIN_IP:", hmac)).isEqualTo(hex).matches("[0-9a-f]{64}");
    }

    @Test
    void 이벤트용_HMAC과_카운터_키는_서로_다르다() {
        AuthRateLimitHasher hasher = hasher();
        String hmac = hasher.hmac("203.0.113.9");

        assertThat(hasher.counterKey("LOGIN_IP:", hmac)).isNotEqualTo(hmac);
    }

    @Test
    void 키_누락과_빈_값은_기동을_막는다() {
        assertThatThrownBy(() -> AuthRateLimitHasher.keyFromBase64(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AUTH_RATE_LIMIT_HMAC_SECRET");
        assertThatThrownBy(() -> AuthRateLimitHasher.keyFromBase64("  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("설정되지 않았습니다");
    }

    @Test
    void Base64가_아니면_거부하고_입력값을_메시지에_넣지_않는다() {
        String bad = "%%%not-base64%%%";

        assertThatThrownBy(() -> AuthRateLimitHasher.keyFromBase64(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64 형식")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(bad))
                .satisfies(e -> assertThat(e.getCause()).isNull());
    }

    @Test
    void 디코딩_결과가_32바이트_미만이면_거부한다() {
        String shortKey = randomBase64(16);

        assertThatThrownBy(() -> AuthRateLimitHasher.keyFromBase64(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트 이상")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(shortKey));
    }

    @Test
    void 올바른_키는_HmacSHA256_키가_된다() {
        SecretKey key = AuthRateLimitHasher.keyFromBase64(randomBase64(32));

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
        assertThat(new SecretKeySpec(key.getEncoded(), "HmacSHA256")).isNotNull();
    }

    @Test
    void 설정_객체의_toString은_HMAC_키를_가린다() {
        String secret = randomBase64(32);
        AuthRateLimitProperties properties = new AuthRateLimitProperties(secret, 1000, 600000, 60000);

        assertThat(properties.toString()).doesNotContain(secret).contains("[REDACTED]");
    }

    @Test
    void 정리_배치_크기가_0이하이면_거부한다() {
        assertThatThrownBy(() -> new AuthRateLimitProperties("x", 0, 600000, 60000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
