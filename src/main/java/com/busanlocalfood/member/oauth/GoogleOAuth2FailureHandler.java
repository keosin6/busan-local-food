package com.busanlocalfood.member.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Google 콜백 실패 처리 (API_SPEC 2.5, 2.6.1). state 불일치, Google 인증 오류, 사용자 취소를 포함한다.
 * 기존 인증 상태를 복원하고 실패 사유 원문은 노출하지 않는다.
 *
 * - 계정 연동 진행 중이었다면 연동 진행 상태를 지우고 /mypage?error=google_failed로 보낸다. 기존 로그인과 JWT는 그대로다.
 * - 일반 로그인이었다면 /login?error=google_failed로 보낸다.
 */
public final class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        boolean linking = GoogleAccountLinkPending.peek(request.getSession(false)).isPresent();
        GoogleAccountLinkPending.clear(request.getSession(false));
        GoogleOAuth2AuthenticationRestore.restorePrevious(request, response);
        response.sendRedirect(linking
                ? GoogleOAuth2SuccessHandler.LINK_FAILED
                : GoogleOAuth2SuccessHandler.ERROR_GOOGLE_FAILED);
    }
}
