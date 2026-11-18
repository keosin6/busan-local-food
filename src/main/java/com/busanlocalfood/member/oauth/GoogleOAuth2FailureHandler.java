package com.busanlocalfood.member.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Google 콜백 실패 처리 (API_SPEC 2.5). state 불일치, Google 인증 오류, 사용자 취소를 포함한다.
 * 기존 인증 상태를 복원하고 /login?error=google_failed로 보낸다. 실패 사유 원문은 노출하지 않는다.
 */
public final class GoogleOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        GoogleOAuth2AuthenticationRestore.restorePrevious(request, response);
        response.sendRedirect(GoogleOAuth2SuccessHandler.ERROR_GOOGLE_FAILED);
    }
}
