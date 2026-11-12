package com.busanlocalfood.member.jwt;

import com.busanlocalfood.member.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

/** Access Token 발급. 서명 알고리즘은 HS256으로 고정한다. */
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder encoder, Duration ttl, Clock clock) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Access Token 만료 시간은 양수여야 합니다.");
        }
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /** sub, iss, aud, iat, exp, role만 포함한다. 이메일, 닉네임, 비밀번호, family_id는 넣지 않는다. */
    public IssuedAccessToken issue(long memberId, Role role) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId는 1 이상이어야 합니다.");
        }
        // JWT 시간 클레임은 초 단위이므로 발급 시각을 초 단위로 맞춘다.
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtSpec.ISSUER)
                .subject(Long.toString(memberId))
                .audience(List.of(JwtSpec.AUDIENCE))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(JwtSpec.CLAIM_ROLE, role.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new IssuedAccessToken(jwt.getTokenValue(), issuedAt, expiresAt);
    }
}
