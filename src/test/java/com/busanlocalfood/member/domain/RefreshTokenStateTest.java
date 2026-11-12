package com.busanlocalfood.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenStateTest {

    private static final String HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String FAMILY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private final Member member = Member.create("s@example.com", "$2a$10$hash", "상태", null);

    @Test
    void 폐기는_처음_시각을_유지하고_중복_호출에도_안전하다() {
        RefreshToken token = RefreshToken.issueInFamily(member, FAMILY, HASH, LocalDateTime.now().plusDays(1));
        LocalDateTime first = LocalDateTime.of(2026, 11, 12, 10, 0);

        token.revoke(first);
        token.revoke(LocalDateTime.of(2026, 11, 12, 11, 0));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void 폐기_시각이_null이면_거부한다() {
        RefreshToken token = RefreshToken.issueInFamily(member, FAMILY, HASH, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> token.revoke(null)).isInstanceOf(NullPointerException.class);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void 만료는_만료_시각과_같은_순간부터_만료로_본다() {
        LocalDateTime expires = LocalDateTime.of(2026, 11, 12, 12, 0);
        RefreshToken token = RefreshToken.issueInFamily(member, FAMILY, HASH, expires);

        assertThat(token.isExpiredAt(expires.minusSeconds(1))).isFalse();
        assertThat(token.isExpiredAt(expires)).isTrue();
        assertThat(token.isExpiredAt(expires.plusSeconds(1))).isTrue();
    }
}
