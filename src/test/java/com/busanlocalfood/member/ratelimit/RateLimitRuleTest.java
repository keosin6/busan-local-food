package com.busanlocalfood.member.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private final RateLimitRule rule = new RateLimitRule(Duration.ofMinutes(15), 5);

    @Test
    void 한도보다_적으면_허용한다() {
        List<LocalDateTime> recent = List.of(NOW.minusSeconds(10), NOW.minusSeconds(20));

        assertThat(rule.evaluate(recent, NOW)).isInstanceOf(RateLimitDecision.Allowed.class);
    }

    @Test
    void 한도에_도달하면_제한하고_기준_이벤트가_창을_벗어날_때까지_남은_초를_알려준다() {
        // 최신순 5건. 5번째(가장 오래된 기준)가 발생한 시각에서 15분 뒤 창을 벗어난다.
        LocalDateTime boundary = NOW.minusMinutes(1);
        List<LocalDateTime> recent = List.of(NOW, NOW.minusSeconds(5), NOW.minusSeconds(10),
                NOW.minusSeconds(30), boundary);

        RateLimitDecision decision = rule.evaluate(recent, NOW);

        assertThat(decision).isEqualTo(new RateLimitDecision.Limited(14 * 60));
    }

    @Test
    void 남은_시간은_올림하여_최소_1초다() {
        LocalDateTime boundary = NOW.minusMinutes(15).plusNanos(1);
        List<LocalDateTime> recent = List.of(NOW, NOW, NOW, NOW, boundary);

        // 기준 시각 + 15분 - 현재 = 1나노초 → 올림하면 1초
        assertThat(rule.evaluate(recent, NOW)).isEqualTo(new RateLimitDecision.Limited(1));
    }

    @Test
    void 창_하한은_현재_시각에서_기간을_뺀_값이다() {
        assertThat(rule.since(NOW)).isEqualTo(NOW.minusMinutes(15));
    }

    @Test
    void 잘못된_규칙_값은_거부한다() {
        assertThatThrownBy(() -> new RateLimitRule(Duration.ZERO, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(Duration.ofMinutes(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
