package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;

/** Google 콜백 실패: state 불일치와 인증 오류. 기존 로그인 상태는 그대로 둔다. */
class GoogleOAuth2FailureHandlerTest {

    @Test
    void 실패하면_google_failed로_가고_기존_로그인_상태를_유지한다() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken("oauth-attempt", null, List.of())));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION, existing);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GoogleOAuth2FailureHandler().onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException("authorization_request_not_found"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_failed");
        SecurityContext restored = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(restored.getAuthentication()).isSameAs(existing);
    }
}
