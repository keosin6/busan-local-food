package com.busanlocalfood.member.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** JWT 테스트 공통 도구. 테스트마다 임의 키를 만들고, 시각은 MutableClock으로 고정·이동한다. */
final class JwtTestSupport {

    static final Instant START = Instant.parse("2026-11-12T00:00:00Z");

    private JwtTestSupport() {
    }

    static SecretKey randomKey(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return new SecretKeySpec(key, "HmacSHA256");
    }

    static JwtEncoder encoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    static JwtDecoder decoder(SecretKey key, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.accessTokenValidator(clock));
        return decoder;
    }

    /** 검증 대상 토큰을 임의 클레임으로 서명한다. 시간 클레임은 초 단위 epoch 값이다. */
    static String sign(JwtEncoder encoder, Map<String, Object> claims) {
        JwtClaimsSet set = JwtClaimsSet.builder().claims(c -> c.putAll(claims)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), set))
                .getTokenValue();
    }

    /** 확정 클레임을 모두 가진 정상 클레임 묶음. */
    static Map<String, Object> validClaims(Instant issuedAt, Duration ttl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "busan-local-food");
        claims.put("aud", List.of("busan-local-food-api"));
        claims.put("sub", "42");
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", issuedAt.plus(ttl).getEpochSecond());
        claims.put("role", "USER");
        return claims;
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
