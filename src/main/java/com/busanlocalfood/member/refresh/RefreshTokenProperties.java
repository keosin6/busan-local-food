package com.busanlocalfood.member.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Refresh Token 만료 설정. Access Token 만료(JwtProperties)와 분리한다.
 * 환경 변수 JWT_REFRESH_TOKEN_EXPIRY_SECONDS, 기본 1209600초(14일).
 */
@ConfigurationProperties(prefix = "jwt")
public record RefreshTokenProperties(@DefaultValue("1209600") long refreshTokenExpirySeconds) {

    public RefreshTokenProperties {
        if (refreshTokenExpirySeconds <= 0) {
            throw new IllegalArgumentException("JWT_REFRESH_TOKEN_EXPIRY_SECONDS는 1 이상이어야 합니다.");
        }
    }
}
