package com.busanlocalfood.member.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 전용 JWT 인증 필터. access_token 쿠키의 Access Token을 기존 AccessTokenVerifier로 검증한다.
 *
 * - 매 요청마다 새 SecurityContext를 만든다. 이전 인증 정보를 이어받지 않는다.
 * - 검증에 성공하면 AccessTokenAuthentication을 설정하고, 실패하면 인증 없이 진행한다.
 *   보호된 API의 401 응답은 인증 진입점이 만든다. 이 필터는 쿠키를 지우거나 바꾸지 않는다.
 * - 토큰 원문, 검증 실패 원인, 클레임은 로그에 남기지 않는다.
 * - 서블릿 필터 빈으로 등록하지 않는다. API 체인에만 추가한다. 화면 요청에는 실행되지 않는다.
 */
public final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "access_token";

    private final AccessTokenVerifier verifier;

    public AccessTokenAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        String token = accessTokenCookie(request);
        if (token != null) {
            try {
                context.setAuthentication(new AccessTokenAuthentication(verifier.verify(token)));
            } catch (InvalidAccessTokenException e) {
                // 인증 없이 진행한다. 원인은 기록하지 않는다.
            }
        }
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    static String accessTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }
}
