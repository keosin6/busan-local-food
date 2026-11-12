package com.busanlocalfood.member.refresh;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 발급된 Refresh Token 원문과 만료 시각. 원문은 쿠키로만 전달하고 응답 본문, 로그, toString()에 넣지 않는다.
 * 이 값은 DB Entity에 들어가지 않는다.
 */
public record IssuedRefreshToken(String token, LocalDateTime expiresAt) {

    public IssuedRefreshToken {
        Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh Token이 비어 있습니다.");
        }
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[token=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
