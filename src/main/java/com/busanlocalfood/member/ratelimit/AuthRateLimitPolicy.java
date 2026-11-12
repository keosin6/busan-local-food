package com.busanlocalfood.member.ratelimit;

import java.time.Duration;

/**
 * 확정 요청 제한 값 (API_SPEC 1.4, DATABASE_ERD 2.10). 설정으로 바꾸지 않는다.
 * 이벤트 보존 기간은 생성 후 24시간이다. 제한 창과 보존 기준을 섞지 않는다.
 */
final class AuthRateLimitPolicy {

    static final Duration RETENTION = Duration.ofHours(24);

    /** 로그인 이메일+IP 조합: 15분 안에 실패 5회까지 허용. */
    static final RateLimitRule LOGIN_PAIR = new RateLimitRule(Duration.ofMinutes(15), 5);
    /** 로그인 IP 보조: 15분 안에 실패 20회까지 허용. */
    static final RateLimitRule LOGIN_IP = new RateLimitRule(Duration.ofMinutes(15), 20);
    /** 회원가입 IP: 1시간 안에 5회까지 허용. */
    static final RateLimitRule SIGNUP_IP = new RateLimitRule(Duration.ofHours(1), 5);
    /** Refresh 계열: 1분 안에 10회까지 허용. */
    static final RateLimitRule REFRESH_FAMILY = new RateLimitRule(Duration.ofMinutes(1), 10);
    /** Google OAuth2 IP: 15분 안에 10회까지 허용. 로그인 시작, 연동 시작, 콜백이 같은 카운터를 쓴다. */
    static final RateLimitRule GOOGLE_OAUTH_IP = new RateLimitRule(Duration.ofMinutes(15), 10);

    /** 카운터 잠금 키 접두어 (DATABASE_ERD 2.10). */
    static final String LOGIN_PAIR_PREFIX = "LOGIN_PAIR:";
    static final String LOGIN_IP_PREFIX = "LOGIN_IP:";
    static final String SIGNUP_IP_PREFIX = "SIGNUP_IP:";
    static final String REFRESH_FAMILY_PREFIX = "REFRESH_FAMILY:";
    static final String GOOGLE_OAUTH_IP_PREFIX = "GOOGLE_OAUTH_IP:";

    private AuthRateLimitPolicy() {
    }
}
