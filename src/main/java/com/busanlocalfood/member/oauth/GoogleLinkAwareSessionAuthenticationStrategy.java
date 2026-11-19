package com.busanlocalfood.member.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 * Google 로그인 성공 시 세션 ID를 바꾸는 기본 전략을, 계정 연동 모드에서만 건너뛴다 (DEVELOPMENT_RULES 5.7.1 원칙 5).
 *
 * 연동 모드에서는 기존 로컬 회원의 세션을 그대로 유지해야 하므로 세션 ID를 바꾸지 않는다.
 * 일반 Google 로그인과 그 밖의 경로는 기존 전략(ChangeSessionId)을 그대로 쓴다.
 */
public final class GoogleLinkAwareSessionAuthenticationStrategy implements SessionAuthenticationStrategy {

    private final SessionAuthenticationStrategy delegate = new ChangeSessionIdAuthenticationStrategy();

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (isLinkInProgress(request.getSession(false))) {
            return;
        }
        delegate.onAuthentication(authentication, request, response);
    }

    static boolean isLinkInProgress(HttpSession session) {
        return GoogleAccountLinkPending.peek(session).isPresent();
    }
}
