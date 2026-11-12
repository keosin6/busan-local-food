package com.busanlocalfood.member.jwt;

import java.time.Instant;

/** 발급된 Access Token. token 값은 쿠키로만 전달하고 JSON 응답 본문에 넣지 않는다. */
public record IssuedAccessToken(String token, Instant issuedAt, Instant expiresAt) {
}
