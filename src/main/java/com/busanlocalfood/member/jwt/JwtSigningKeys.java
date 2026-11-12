package com.busanlocalfood.member.jwt;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * JWT 서명 키 로딩. 시작 시 검증하며, 실패하면 애플리케이션 기동을 막는다.
 * 오류 메시지에는 키 값과 디코딩 결과를 넣지 않는다. 원인 예외도 연결하지 않는다.
 */
final class JwtSigningKeys {

    private JwtSigningKeys() {
    }

    static SecretKey fromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경 변수가 설정되지 않았습니다. Base64로 인코딩한 32바이트 이상의 키가 필요합니다.");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            // 원인 예외를 연결하지 않는다. 예외 메시지에 키 문자가 포함될 수 있기 때문이다.
            throw new IllegalStateException("JWT_SECRET은 Base64 형식이어야 합니다.");
        }
        if (key.length < JwtSpec.MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET의 디코딩 결과는 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
