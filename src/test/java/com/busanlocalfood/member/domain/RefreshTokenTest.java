package com.busanlocalfood.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final String VALID_HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    private final Member member = Member.create("a@example.com", "$2a$10$hash", "부산먹보", null);

    @Test
    void 해시로_행을_만들면_해시와_만료_시각을_보관한다() {
        LocalDateTime expires = LocalDateTime.of(2026, 12, 1, 0, 0);

        RefreshToken token = RefreshToken.issue(member, VALID_HASH, expires);

        assertThat(token.getTokenHash()).isEqualTo(VALID_HASH);
        assertThat(token.getExpiresAt()).isEqualTo(expires);
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getMember()).isSameAs(member);
    }

    @Test
    void 해시_형식이_아니면_거부한다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);

        // 원문 토큰처럼 보이는 값, 길이가 다른 값, 대문자 16진수는 모두 거부한다.
        assertThatThrownBy(() -> RefreshToken.issue(member, "raw-refresh-token-value", expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(member, VALID_HASH.substring(1), expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(member, VALID_HASH.toUpperCase(), expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(member, null, expires))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
