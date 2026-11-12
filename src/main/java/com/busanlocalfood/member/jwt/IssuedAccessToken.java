package com.busanlocalfood.member.jwt;

import java.time.Instant;

/**
 * 발급된 Access Token. token 값은 쿠키로만 전달하고 JSON 응답 본문에 넣지 않는다.
 * 기본 toString()에 token 원문이 노출되지 않도록 마스킹한다.
 */
public record IssuedAccessToken(String token, Instant issuedAt, Instant expiresAt) {

    private static final String REDACTED = "[REDACTED]";

    @Override
    public String toString() {
        return "IssuedAccessToken[token=" + REDACTED
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
