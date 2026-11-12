package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.member.domain.Role;
import java.time.Duration;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AccessTokenIssuanceTest {

    private final SecretKey key = JwtTestSupport.randomKey(32);
    private final JwtTestSupport.MutableClock clock = new JwtTestSupport.MutableClock(JwtTestSupport.START);

    private AccessTokenIssuer issuer(Duration ttl) {
        return new AccessTokenIssuer(JwtTestSupport.encoder(key), ttl, clock);
    }

    private Jwt decode(String token) {
        JwtDecoder decoder = JwtTestSupport.decoder(key, clock);
        return decoder.decode(token);
    }

    @Test
    void 정상_토큰을_발급하고_HS256으로_서명한다() {
        IssuedAccessToken issued = issuer(Duration.ofSeconds(1800)).issue(42L, Role.USER);

        Jwt jwt = decode(issued.token());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(issued.token()).isNotBlank();
    }

    @Test
    void sub_iss_aud_role을_확정값으로_담는다() {
        IssuedAccessToken issued = issuer(Duration.ofSeconds(1800)).issue(42L, Role.ADMIN);

        Jwt jwt = decode(issued.token());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("busan-local-food");
        assertThat(jwt.getAudience()).containsExactly("busan-local-food-api");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void iat과_exp는_발급_시각과_설정된_만료_시간으로_계산된다() {
        IssuedAccessToken issued = issuer(Duration.ofSeconds(1800)).issue(42L, Role.USER);

        Jwt jwt = decode(issued.token());
        assertThat(jwt.getIssuedAt()).isEqualTo(JwtTestSupport.START);
        assertThat(jwt.getExpiresAt()).isEqualTo(JwtTestSupport.START.plusSeconds(1800));
        assertThat(issued.issuedAt()).isEqualTo(JwtTestSupport.START);
        assertThat(issued.expiresAt()).isEqualTo(JwtTestSupport.START.plusSeconds(1800));
    }

    @Test
    void 설정한_만료_시간이_적용된다() {
        IssuedAccessToken issued = issuer(Duration.ofSeconds(600)).issue(42L, Role.USER);

        Jwt jwt = decode(issued.token());
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(600);
    }

    @Test
    void 토큰_클레임은_확정된_항목만_포함한다() {
        IssuedAccessToken issued = issuer(Duration.ofSeconds(1800)).issue(42L, Role.USER);

        Jwt jwt = decode(issued.token());
        assertThat(jwt.getClaims().keySet())
                .isEqualTo(Set.of("sub", "iss", "aud", "iat", "exp", "role"));
    }

    @Test
    void 발급_시각은_초_단위로_맞춰진다() {
        JwtTestSupport.MutableClock withMillis = new JwtTestSupport.MutableClock(
                JwtTestSupport.START.plusMillis(789));
        IssuedAccessToken issued = new AccessTokenIssuer(JwtTestSupport.encoder(key),
                Duration.ofSeconds(1800), withMillis).issue(42L, Role.USER);

        assertThat(issued.issuedAt()).isEqualTo(JwtTestSupport.START);
    }

    @Test
    void 잘못된_회원_ID나_만료_시간은_발급하지_않는다() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> issuer(Duration.ofSeconds(1800)).issue(0L, Role.USER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> issuer(Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
