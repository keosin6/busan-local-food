package com.busanlocalfood.member.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * 요청 제한 식별자 계산.
 * - hmac(): 이벤트 테이블에 저장하는 IP·이메일 식별자 (HMAC-SHA256, 소문자 hex 64자).
 * - counterKey(): auth_rate_limit_counter의 잠금 키 (접두어와 식별값을 이은 문자열의 SHA-256).
 * 두 값은 서로 다르며 섞어 쓰지 않는다. 키 값과 입력 원문은 예외 메시지에 넣지 않는다.
 */
public final class AuthRateLimitHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;

    public AuthRateLimitHasher(SecretKey key) {
        this.key = Objects.requireNonNull(key, "key는 필수입니다.");
    }

    /** AUTH_RATE_LIMIT_HMAC_SECRET 값을 키로 바꾼다. 누락, Base64 오류, 길이 부족이면 기동을 막는다. */
    public static SecretKey keyFromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "AUTH_RATE_LIMIT_HMAC_SECRET 환경 변수가 설정되지 않았습니다. Base64로 인코딩한 32바이트 이상의 키가 필요합니다.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            // 원인 예외를 연결하지 않는다. 예외 메시지에 키 문자가 섞일 수 있기 때문이다.
            throw new IllegalStateException("AUTH_RATE_LIMIT_HMAC_SECRET은 Base64 형식이어야 합니다.");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("AUTH_RATE_LIMIT_HMAC_SECRET의 디코딩 결과는 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(bytes, HMAC_ALGORITHM);
    }

    public String hmac(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("식별 값이 비어 있습니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256을 초기화할 수 없습니다.");
        }
    }

    /** 카운터 잠금 키. 접두어(예: LOGIN_IP:)와 식별값을 이은 문자열의 SHA-256 hex다. */
    public String counterKey(String prefix, String identifier) {
        if (prefix == null || identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("카운터 키 입력이 올바르지 않습니다.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((prefix + identifier).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }
}
