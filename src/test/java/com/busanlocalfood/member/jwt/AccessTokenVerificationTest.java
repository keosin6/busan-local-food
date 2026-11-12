package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.member.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AccessTokenVerificationTest {

    private static final Duration TTL = Duration.ofSeconds(1800);

    private SecretKey key;
    private JwtTestSupport.MutableClock clock;
    private AccessTokenVerifier verifier;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        key = JwtTestSupport.randomKey(32);
        clock = new JwtTestSupport.MutableClock(JwtTestSupport.START);
        encoder = JwtTestSupport.encoder(key);
        verifier = new AccessTokenVerifier(JwtTestSupport.decoder(key, clock));
    }

    private String validToken() {
        return new AccessTokenIssuer(encoder, TTL, clock).issue(42L, Role.USER).token();
    }

    private String sign(Map<String, Object> claims) {
        return JwtTestSupport.sign(encoder, claims);
    }

    private Map<String, Object> valid() {
        return JwtTestSupport.validClaims(JwtTestSupport.START, TTL);
    }

    // ---- 정상 ----

    @Test
    void 정상_토큰은_회원_ID와_권한을_돌려준다() {
        assertThat(verifier.verify(validToken())).isEqualTo(new AuthenticatedMember(42L, Role.USER));
    }

    @Test
    void ADMIN_권한도_정상적으로_검증된다() {
        String token = new AccessTokenIssuer(encoder, TTL, clock).issue(7L, Role.ADMIN).token();

        assertThat(verifier.verify(token)).isEqualTo(new AuthenticatedMember(7L, Role.ADMIN));
    }

    // ---- 서명·알고리즘 ----

    @Test
    void 페이로드를_변조하면_거부한다() {
        String token = validToken();
        String[] parts = token.split("\\.");
        String forgedJson = "{\"sub\":\"999\",\"iss\":\"busan-local-food\",\"aud\":[\"busan-local-food-api\"],"
                + "\"iat\":" + JwtTestSupport.START.getEpochSecond() + ",\"exp\":"
                + JwtTestSupport.START.plusSeconds(1800).getEpochSecond() + ",\"role\":\"USER\"}";
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedJson.getBytes(StandardCharsets.UTF_8));
        // 원래 서명을 그대로 붙인 위조 토큰
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        SecretKey other = JwtTestSupport.randomKey(32);
        String token = JwtTestSupport.sign(JwtTestSupport.encoder(other), valid());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void alg_none_토큰은_거부한다() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"42\",\"iss\":\"busan-local-food\",\"aud\":[\"busan-local-food-api\"],"
                        + "\"iat\":" + JwtTestSupport.START.getEpochSecond() + ",\"exp\":"
                        + JwtTestSupport.START.plusSeconds(1800).getEpochSecond() + ",\"role\":\"USER\"}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> verifier.verify(header + "." + payload + "."))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void HS384로_서명한_토큰은_거부한다() {
        SecretKey longKey = JwtTestSupport.randomKey(48);
        JwtEncoder hs384Encoder = JwtTestSupport.encoder(longKey);
        JwtClaimsSet set = JwtClaimsSet.builder().claims(c -> c.putAll(valid())).build();
        String token = hs384Encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS384).build(), set)).getTokenValue();

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 형식이_깨진_문자열은_거부한다() {
        assertThatThrownBy(() -> verifier.verify("not.a.jwt")).isInstanceOf(InvalidAccessTokenException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 토큰이_없으면_거부한다(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    // ---- 발급자·대상 ----

    @Test
    void 발급자가_다르면_거부한다() {
        Map<String, Object> claims = valid();
        claims.put("iss", "other-issuer");

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 대상이_다르면_거부한다() {
        Map<String, Object> claims = valid();
        claims.put("aud", List.of("other-audience"));

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    // ---- 만료와 시계 오차 ----

    @Test
    void 만료된_토큰은_거부한다() {
        String token = validToken();
        clock.advance(TTL.plusSeconds(61));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 만료_후_60초_이내의_오차는_허용한다() {
        String token = validToken();
        clock.advance(TTL.plusSeconds(59));

        assertThat(verifier.verify(token)).isEqualTo(new AuthenticatedMember(42L, Role.USER));
    }

    @Test
    void 만료_후_60초를_초과한_오차는_거부한다() {
        String token = validToken();
        clock.advance(TTL.plusSeconds(61));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 미래_발급_시각이_60초_이내면_허용한다() {
        Map<String, Object> claims = valid();
        claims.put("iat", JwtTestSupport.START.plusSeconds(59).getEpochSecond());
        claims.put("exp", JwtTestSupport.START.plusSeconds(59).plus(TTL).getEpochSecond());

        assertThat(verifier.verify(sign(claims))).isEqualTo(new AuthenticatedMember(42L, Role.USER));
    }

    @Test
    void 미래_발급_시각이_60초를_넘으면_거부한다() {
        Map<String, Object> claims = valid();
        claims.put("iat", JwtTestSupport.START.plusSeconds(61).getEpochSecond());
        claims.put("exp", JwtTestSupport.START.plusSeconds(61).plus(TTL).getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    // ---- 필수 클레임 ----

    @ParameterizedTest
    @ValueSource(strings = {"iss", "aud", "sub", "iat", "exp", "role"})
    void 필수_클레임이_없으면_거부한다(String missing) {
        Map<String, Object> claims = valid();
        claims.remove(missing);

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    // ---- 회원 ID·권한 형식 ----

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "01", "1.5", " 42", "99999999999999999999"})
    void 잘못된_회원_ID_형식은_거부한다(String subject) {
        Map<String, Object> claims = valid();
        claims.put("sub", subject);

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "user", "", "ADMIN ", "USER,ADMIN"})
    void 허용되지_않은_권한_값은_거부한다(String role) {
        Map<String, Object> claims = valid();
        claims.put("role", role);

        assertThatThrownBy(() -> verifier.verify(sign(claims))).isInstanceOf(InvalidAccessTokenException.class);
    }

    // ---- 외부 노출 ----

    @Test
    void 검증_실패_예외_메시지에_토큰_원문이_없다() {
        String token = validToken();
        clock.advance(TTL.plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(token))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(token));
    }
}
