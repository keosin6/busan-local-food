package com.busanlocalfood.member.ratelimit;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 요청 제한 이벤트의 주기 정리 (R3). 설정한 주기마다 24시간이 지난 이벤트를 배치로 지운다.
 * 실패는 로그에 예외 종류만 남기고 넘어간다. 다음 주기에 다시 시도한다.
 */
@Component
public class AuthRateLimitCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitCleanupScheduler.class);
    static final int MAX_BATCHES_PER_TABLE = 50;

    private final AuthRateLimitCleanup cleanup;
    private final AuthRateLimitProperties properties;
    private final Clock clock;

    public AuthRateLimitCleanupScheduler(AuthRateLimitCleanup cleanup,
                                         AuthRateLimitProperties properties,
                                         Clock clock) {
        this.cleanup = cleanup;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${auth.rate-limit.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${auth.rate-limit.cleanup-interval-ms:600000}")
    public void purgeExpiredEvents() {
        try {
            int deleted = cleanup.purgeExpired(
                    LocalDateTime.now(clock), properties.cleanupBatchSize(), MAX_BATCHES_PER_TABLE);
            log.info("요청 제한 이벤트 주기 정리 완료: 삭제 {}건", deleted);
        } catch (RuntimeException e) {
            log.warn("요청 제한 이벤트 주기 정리 실패: 원인={}", e.getClass().getSimpleName());
        }
    }
}
