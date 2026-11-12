package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.member.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** 기본 toString()에 Access Token 원문과 Base64 서명 키가 노출되지 않는지 검증한다. */
class SensitiveToStringTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-12T00:30:00Z");

    @Test
    void issuedAccessTokenToStringDoesNotContainRawToken() {
        SecretKey key = JwtTestSupport.randomKey(32);
        AccessTokenIssuer issuer = new AccessTokenIssuer(
                JwtTestSupport.encoder(key), Duration.ofMinutes(30),
                new JwtTestSupport.MutableClock(JwtTestSupport.START));

        IssuedAccessToken issued = issuer.issue(7L, Role.USER);
        String text = issued.toString();

        assertThat(issued.token()).isNotBlank();
        assertThat(text).doesNotContain(issued.token());
        assertThat(text).contains("[REDACTED]");
    }

    @Test
    void issuedAccessTokenToStringKeepsTimestampsAndAccessorsWork() {
        IssuedAccessToken issued = new IssuedAccessToken("raw.token.value", ISSUED_AT, EXPIRES_AT);

        String text = issued.toString();

        assertThat(text).doesNotContain("raw.token.value");
        assertThat(text).contains(ISSUED_AT.toString(), EXPIRES_AT.toString());
        assertThat(issued.token()).isEqualTo("raw.token.value");
        assertThat(issued.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(issued.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void issuedAccessTokenToStringDoesNotThrowForNullFields() {
        IssuedAccessToken issued = new IssuedAccessToken(null, null, null);

        assertThat(issued.toString()).contains("[REDACTED]");
    }

    @Test
    void jwtPropertiesToStringDoesNotContainBase64Secret() {
        String secret = Base64.getEncoder().encodeToString(JwtTestSupport.randomKey(32).getEncoded());
        JwtProperties properties = new JwtProperties(secret, 1800);

        String text = properties.toString();

        assertThat(text).doesNotContain(secret);
        assertThat(text).contains("[REDACTED]");
    }

    @Test
    void jwtPropertiesToStringKeepsExpiryAndAccessorsWork() {
        String secret = "c2VjcmV0LWZvci10ZXN0LW9ubHktMzItYnl0ZXMtbWluaW11bQ==";
        JwtProperties properties = new JwtProperties(secret, 900);

        String text = properties.toString();

        assertThat(text).contains("accessTokenExpirySeconds=900");
        assertThat(properties.secret()).isEqualTo(secret);
        assertThat(properties.accessTokenExpirySeconds()).isEqualTo(900);
    }

    @Test
    void jwtPropertiesToStringDoesNotThrowForNullSecret() {
        JwtProperties properties = new JwtProperties(null, 1800);

        assertThat(properties.toString()).contains("[REDACTED]", "accessTokenExpirySeconds=1800");
    }
}
