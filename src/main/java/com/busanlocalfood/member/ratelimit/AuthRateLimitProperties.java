package com.busanlocalfood.member.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 요청 제한 설정. hmacSecret은 환경 변수 AUTH_RATE_LIMIT_HMAC_SECRET으로만 주입한다 (Base64, 디코딩 32바이트 이상).
 * 기본값을 두지 않는다. 검증은 AuthRateLimitConfig가 기동 시 수행한다.
 */
@ConfigurationProperties(prefix = "auth.rate-limit")
public record AuthRateLimitProperties(
        @DefaultValue("") String hmacSecret,
        @DefaultValue("1000") int cleanupBatchSize,
        @DefaultValue("600000") long cleanupIntervalMs,
        @DefaultValue("60000") long cleanupInitialDelayMs) {

    public AuthRateLimitProperties {
        if (cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("auth.rate-limit.cleanup-batch-size는 1 이상이어야 합니다.");
        }
        if (cleanupIntervalMs <= 0 || cleanupInitialDelayMs < 0) {
            throw new IllegalArgumentException("auth.rate-limit 정리 주기 설정이 올바르지 않습니다.");
        }
    }

    /** HMAC 키 값은 출력하지 않는다. */
    @Override
    public String toString() {
        return "AuthRateLimitProperties[hmacSecret=[REDACTED], cleanupBatchSize=" + cleanupBatchSize
                + ", cleanupIntervalMs=" + cleanupIntervalMs
                + ", cleanupInitialDelayMs=" + cleanupInitialDelayMs + "]";
    }
}
