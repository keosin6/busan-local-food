package com.busanlocalfood.member.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Google 콜백 처리 중 Spring이 넣은 OAuth2 인증을 지우고, 콜백 전의 인증 상태로 되돌린다.
 * 정식 로그인이 아닌 결과(신규 가입 진행, 오류)에서 기존 로그인 세션을 바꾸지 않기 위해서다.
 * 콜백 전에 로그인 상태가 없었다면 세션의 인증 정보를 비운다.
 */
final class GoogleOAuth2AuthenticationRestore {

    private static final HttpSessionSecurityContextRepository CONTEXT_REPOSITORY =
            new HttpSessionSecurityContextRepository();

    private GoogleOAuth2AuthenticationRestore() {
    }

    static void restorePrevious(HttpServletRequest request, HttpServletResponse response) {
        Object previous = request.getAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (previous instanceof Authentication authentication) {
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
    }
}
