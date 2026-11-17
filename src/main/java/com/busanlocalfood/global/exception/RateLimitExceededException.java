package com.busanlocalfood.global.exception;

/**
 * 요청 제한 초과. GlobalExceptionHandler가 429 RATE_LIMITED와 Retry-After 헤더로 바꾼다.
 * 재시도까지 남은 초(retryAfterSeconds)는 요청 제한 판정 결과에서 온 값이어야 한다.
 * 이 예외의 메시지나 응답 본문에는 제한 키, IP, 이메일, family_id, 제한 횟수를 넣지 않는다.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("요청 제한 초과");
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds는 1 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
