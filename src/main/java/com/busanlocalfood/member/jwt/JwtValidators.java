package com.busanlocalfood.member.jwt;

import com.busanlocalfood.member.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Access Token 검증 규칙 조립. 알고리즘 HS256 고정은 디코더 빌더(macAlgorithm)에서 한다.
 * 시계 오차는 JwtSpec.CLOCK_SKEW(60초)로 명시한다. Spring 기본값에 의존하지 않는다.
 */
final class JwtValidators {

    /** 회원 ID: 양의 정수 문자열. 선행 0은 허용하지 않는다. */
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("^[1-9][0-9]{0,18}$");

    private JwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> accessTokenValidator(Clock clock) {
        JwtTimestampValidator timestamp = new JwtTimestampValidator(JwtSpec.CLOCK_SKEW);
        timestamp.setClock(clock);

        return new DelegatingOAuth2TokenValidator<>(
                requiredClaims(),
                new JwtIssuerValidator(JwtSpec.ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(JwtSpec.AUDIENCE)),
                timestamp,
                issuedNotInFuture(clock),
                subjectFormat(),
                roleFormat());
    }

    private static OAuth2TokenValidator<Jwt> requiredClaims() {
        return jwt -> {
            List<String> missing = new ArrayList<>();
            for (String name : List.of(JwtClaimNames.ISS, JwtClaimNames.AUD, JwtClaimNames.SUB,
                    JwtClaimNames.IAT, JwtClaimNames.EXP, JwtSpec.CLAIM_ROLE)) {
                if (jwt.getClaims().get(name) == null) {
                    missing.add(name);
                }
            }
            return missing.isEmpty() ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(invalid("required claim missing"));
        };
    }

    private static OAuth2TokenValidator<Jwt> issuedNotInFuture(Clock clock) {
        return jwt -> {
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt == null) {
                return OAuth2TokenValidatorResult.failure(invalid("iat missing"));
            }
            Instant limit = clock.instant().plus(JwtSpec.CLOCK_SKEW).truncatedTo(ChronoUnit.SECONDS);
            return issuedAt.isAfter(limit)
                    ? OAuth2TokenValidatorResult.failure(invalid("iat in future"))
                    : OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidator<Jwt> subjectFormat() {
        return jwt -> {
            String sub = jwt.getSubject();
            return sub != null && SUBJECT_PATTERN.matcher(sub).matches()
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(invalid("subject format"));
        };
    }

    private static OAuth2TokenValidator<Jwt> roleFormat() {
        return jwt -> {
            Object role = jwt.getClaims().get(JwtSpec.CLAIM_ROLE);
            return role instanceof String value && isKnownRole(value)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(invalid("role value"));
        };
    }

    static boolean isKnownRole(String value) {
        for (Role role : Role.values()) {
            if (role.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static OAuth2Error invalid(String reason) {
        // 내부 사유만 담는다. 외부 응답에는 노출하지 않는다.
        return new OAuth2Error("invalid_token", reason, null);
    }
}
