package com.busanlocalfood.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 응답마다 XSRF-TOKEN 쿠키를 내려준다 (API_SPEC 1.1). 지연 로딩된 토큰 값을 한 번 읽어 쿠키 저장을 확정한다.
 * CsrfFilter 뒤에 둔다. 인증 여부와 관계없이 실행되므로 인증 전 요청에서도 토큰을 받을 수 있다.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attribute = request.getAttribute(CsrfToken.class.getName());
        if (attribute instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
