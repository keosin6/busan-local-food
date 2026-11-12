package com.busanlocalfood.member.ratelimit;

/**
 * 요청 제한 판정 결과. Limited는 HTTP 계층에서 429 RATE_LIMITED와 Retry-After(초)로 바꾼다.
 * 제한된 요청은 이벤트를 추가하지 않으며, 토큰과 쿠키도 바꾸지 않는다.
 */
public sealed interface RateLimitDecision permits RateLimitDecision.Allowed, RateLimitDecision.Limited {

    record Allowed() implements RateLimitDecision {
    }

    record Limited(long retryAfterSeconds) implements RateLimitDecision {
    }
}
