package com.busanlocalfood.member.ratelimit;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 제한 규칙: 최근 window 동안 limit번까지 허용한다. Rolling Window 기준이다.
 * 판정은 순수 함수로 두어 경계값을 단위 테스트한다. 이벤트 조회는 호출자가 같은 잠금 구간에서 한다.
 */
record RateLimitRule(Duration window, int limit) {

    RateLimitRule {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("제한 기간은 양수여야 합니다.");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("제한 횟수는 1 이상이어야 합니다.");
        }
    }

    /** 이벤트를 세는 하한. 이 시각보다 늦게 발생한 이벤트만 창 안에 있다 (경계 포함 안 함). */
    LocalDateTime since(LocalDateTime now) {
        return now.minus(window);
    }

    /**
     * recentNewestFirst: 창 안의 이벤트 시각을 최신순으로 최대 limit개 받은 목록.
     * limit개 이상이면 제한한다. 가장 오래된 기준 이벤트(limit번째)가 창을 벗어나는 시각까지 기다리면 다시 허용된다.
     */
    RateLimitDecision evaluate(List<LocalDateTime> recentNewestFirst, LocalDateTime now) {
        if (recentNewestFirst.size() < limit) {
            return new RateLimitDecision.Allowed();
        }
        LocalDateTime boundary = recentNewestFirst.get(limit - 1);
        Duration retry = Duration.between(now, boundary.plus(window));
        return new RateLimitDecision.Limited(ceilSeconds(retry));
    }

    private static long ceilSeconds(Duration duration) {
        long seconds = duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
