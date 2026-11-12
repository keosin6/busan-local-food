package com.busanlocalfood.member.refresh;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 원문 생성기. 암호학적으로 안전한 SecureRandom으로 256비트를 뽑아 URL-safe Base64(패딩 없음)로 인코딩한다.
 * Access Token 생성(AccessTokenIssuer)과 책임을 나눈다. 이 클래스는 해시를 만들지 않는다.
 */
@Component
public class RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256비트

    private final SecureRandom random = new SecureRandom();

    /** 새 원문 토큰을 만든다. 원문은 발급 결과로만 전달하고 저장하지 않는다. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
