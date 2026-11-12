package com.busanlocalfood.member.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 설정. 값은 환경 변수에서 application.yml을 거쳐 주입된다.
 * secret: Base64 인코딩한 서명 키 (환경 변수 JWT_SECRET). 키 원문을 로그에 남기지 않는다.
 * accessTokenExpirySeconds: Access Token 만료 초 (환경 변수 JWT_ACCESS_TOKEN_EXPIRY_SECONDS, 기본 1800).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, @DefaultValue("1800") long accessTokenExpirySeconds) {

    private static final String REDACTED = "[REDACTED]";

    public JwtProperties {
        if (accessTokenExpirySeconds <= 0) {
            throw new IllegalArgumentException("JWT_ACCESS_TOKEN_EXPIRY_SECONDS는 1 이상이어야 합니다.");
        }
    }

    /** 기본 toString()에 Base64 서명 키가 노출되지 않도록 secret은 마스킹하고 만료 초는 그대로 표시한다. */
    @Override
    public String toString() {
        return "JwtProperties[secret=" + REDACTED
                + ", accessTokenExpirySeconds=" + accessTokenExpirySeconds + "]";
    }
}
