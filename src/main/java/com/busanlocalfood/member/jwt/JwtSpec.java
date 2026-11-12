package com.busanlocalfood.member.jwt;

import java.time.Duration;

/** JWT 확정 설계 값 (DEVELOPMENT_RULES 5.7, API_SPEC 1.5). 설정으로 바꾸지 않는 값만 둔다. */
final class JwtSpec {

    static final String ISSUER = "busan-local-food";
    static final String AUDIENCE = "busan-local-food-api";
    static final String CLAIM_ROLE = "role";
    /** 시계 오차 허용. 이 값보다 큰 오차는 허용하지 않는다. */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    /** HS256 서명 키의 최소 길이 (256비트). */
    static final int MIN_KEY_BYTES = 32;

    private JwtSpec() {
    }
}
