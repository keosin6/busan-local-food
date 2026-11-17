package com.busanlocalfood.member.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 전용 JWT 인증 필터. Access Token을 다음 두 수단 중 하나에서 읽고, 기존 AccessTokenVerifier로 검증한다.
 *
 * 인증 수단 선택 (API_SPEC 1.0):
 * - Authorization 헤더가 있으면 Bearer 헤더만 본다. 쿠키는 보지 않는다.
 *   Bearer 형식이 잘못되었거나 값이 비어 있으면 쿠키로 대체하지 않고 인증 실패로 처리한다.
 * - Authorization 헤더가 없으면 access_token 쿠키를 본다.
 *
 * 그 밖의 규칙:
 * - 매 요청 새 SecurityContext를 만든다. 이전 인증 정보를 이어받지 않는다.
 * - 검증에 성공하면 AccessTokenAuthentication을 설정하고, 실패하면 인증 없이 진행한다.
 *   보호된 API의 401 응답은 인증 진입점이 만든다.
 * - 쿠키를 지우거나 바꾸지 않는다. 토큰 원문과 검증 실패 원인은 로그에 남기지 않는다.
 * - 서블릿 필터 빈으로 등록하지 않는다. API 체인에만 추가한다. 화면 요청에는 실행되지 않는다.
 */
public final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "access_token";
    private static final String BEARER_SCHEME = "Bearer ";

    private final AccessTokenVerifier verifier;

    public AccessTokenAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * Authorization 헤더의 스킴이 Bearer인지 본다. 대소문자는 구분하지 않는다.
     * CSRF 면제 판단에만 쓴다. 토큰 값은 보지 않으므로 JWT 검증과 분리된다.
     * Basic 등 다른 스킴과 헤더 없음은 false다.
     */
    public static boolean hasBearerScheme(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        String token = resolveToken(request);
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

    /** Bearer 헤더가 있으면 그 값만 쓴다. 없으면 쿠키 값을 쓴다. 쓸 수 없는 값은 null이다. */
    static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return bearerToken(authorization);
        }
        return accessTokenCookie(request);
    }

    /** "Bearer <토큰>" 형식만 받는다. 스킴은 대소문자를 구분하지 않는다. 토큰 안의 공백은 형식 오류다. */
    static String bearerToken(String header) {
        if (!header.regionMatches(true, 0, BEARER_SCHEME, 0, BEARER_SCHEME.length())) {
            return null;
        }
        String token = header.substring(BEARER_SCHEME.length()).trim();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return token;
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
