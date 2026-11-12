package com.busanlocalfood.member.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh Token 원문의 SHA-256 해시 계산. DB에는 이 결과(소문자 16진수 64자)만 저장한다.
 * 입력값은 예외 메시지에 넣지 않는다.
 */
public final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    public static String sha256Hex(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Refresh Token이 비어 있습니다.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
