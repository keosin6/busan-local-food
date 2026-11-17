package com.busanlocalfood.member.web;

import com.busanlocalfood.member.jwt.AccessTokenAuthenticationFilter;
import com.busanlocalfood.member.jwt.IssuedAccessToken;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * JWT 쿠키 발급과 만료 (API_SPEC 1.1). 두 쿠키 모두 HttpOnly, SameSite=Lax, 운영에서만 Secure다.
 * Domain은 설정하지 않는다. 만료 쿠키는 발급 때와 같은 이름과 Path를 쓴다.
 * 쿠키 값(원문 토큰)은 로그에 남기지 않는다.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = AccessTokenAuthenticationFilter.COOKIE_NAME;
    public static final String REFRESH_COOKIE = "refresh_token";
    static final String ACCESS_PATH = "/api";
    static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;
    private final Duration refreshTtl;

    public AuthCookies(Environment environment, RefreshTokenProperties refreshProperties) {
        this.secure = environment.acceptsProfiles(Profiles.of("prod"));
        this.refreshTtl = Duration.ofSeconds(refreshProperties.refreshTokenExpirySeconds());
    }

    public ResponseCookie accessToken(IssuedAccessToken token) {
        return base(ACCESS_COOKIE, token.token(), ACCESS_PATH)
                .maxAge(Duration.between(token.issuedAt(), token.expiresAt()))
                .build();
    }

    public ResponseCookie refreshToken(IssuedRefreshToken token) {
        return base(REFRESH_COOKIE, token.token(), REFRESH_PATH)
                .maxAge(refreshTtl)
                .build();
    }

    /** 두 JWT 쿠키를 만료시킨다. 로그아웃, 재발급 실패와 재사용 탐지에서 쓴다. */
    public List<ResponseCookie> expireAll() {
        return List.of(
                base(ACCESS_COOKIE, "", ACCESS_PATH).maxAge(Duration.ZERO).build(),
                base(REFRESH_COOKIE, "", REFRESH_PATH).maxAge(Duration.ZERO).build());
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path);
    }
}
