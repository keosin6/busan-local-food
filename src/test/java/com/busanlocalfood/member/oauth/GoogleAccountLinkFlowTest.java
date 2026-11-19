package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.MemberAccountService.LinkOutcome;
import com.busanlocalfood.member.web.AuthCookies;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Google 계정 연동 콜백의 세션 보존 (API_SPEC 2.6.1, DEVELOPMENT_RULES 5.7.1). 실제 Google 서버는 쓰지 않는다.
 * 확인하는 것: 연동 결과별 리다이렉트, 기존 로그인 상태 복원, 세션 객체 유지, 쿠키 미발급, 임시 상태 정리.
 */
class GoogleAccountLinkFlowTest {

    private static final String CONTEXT_KEY = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final String STATE = "state-abc";
    private static final String SUB = "google-sub-1";

    private final GoogleLoginService login = mock(GoogleLoginService.class);
    private final AuthApiService authApi = mock(AuthApiService.class);
    private final AuthCookies cookies = mock(AuthCookies.class);
    private final MemberAccountService accounts = mock(MemberAccountService.class);
    private final GoogleOAuth2SuccessHandler success = new GoogleOAuth2SuccessHandler(login, authApi, cookies, accounts);
    private final GoogleOAuth2FailureHandler failure = new GoogleOAuth2FailureHandler();

    private static UsernamePasswordAuthenticationToken localMember(long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static OAuth2AuthenticationToken googleLogin(String sub, boolean verified) {
        Map<String, Object> attributes = Map.of("sub", sub, "email", "g@example.com", "email_verified", verified);
        DefaultOAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    /** 로그인된 로컬 회원의 화면 세션과 연동 진행 상태가 있는 요청을 만든다. */
    private static MockHttpServletRequest linkingRequest(MockHttpSession session, long memberId, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("state", state);
        request.setAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION, localMember(memberId));
        return request;
    }

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CONTEXT_KEY, new SecurityContextImpl(localMember(memberId)));
        return session;
    }

    private static long principalOf(MockHttpSession session) {
        Object context = session.getAttribute(CONTEXT_KEY);
        Authentication authentication = ((SecurityContextImpl) context).getAuthentication();
        return (Long) authentication.getPrincipal();
    }

    @Test
    void 연동_성공은_기존_회원의_인증을_보존하고_세션과_쿠키를_건드리지_않으며_linked로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        when(accounts.linkGoogle(7L, SUB)).thenReturn(LinkOutcome.LINKED);
        MockHttpServletRequest request = linkingRequest(session, 7L, STATE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication google = googleLogin(SUB, true);

        success.onAuthenticationSuccess(request, response, google);

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?google=linked");
        assertThat(request.getSession(false)).isSameAs(session);
        assertThat(principalOf(session)).isEqualTo(7L);
        assertThat(response.getCookies()).isEmpty();
        assertThat(session.getAttribute(GoogleAccountLinkPending.SESSION_KEY)).isNull();
        verify(login, never()).decide(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(authApi, never()).issueSession(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 이미_같은_회원에게_연결되어_있어도_멱등하게_linked로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        when(accounts.linkGoogle(7L, SUB)).thenReturn(LinkOutcome.LINKED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, STATE), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?google=linked");
    }

    @Test
    void 미검증_이메일은_연동하지_않고_google_unverified로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, STATE), response, googleLogin(SUB, false));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_unverified");
        verify(accounts, never()).linkGoogle(anyLong(), anyString());
        assertThat(principalOf(session)).isEqualTo(7L);
    }

    @Test
    void 다른_회원에게_연결된_sub면_google_already_linked로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        when(accounts.linkGoogle(7L, SUB)).thenReturn(LinkOutcome.ALREADY_LINKED_TO_OTHER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, STATE), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_already_linked");
        assertThat(principalOf(session)).isEqualTo(7L);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void 동시_요청으로_UNIQUE가_깨지면_google_already_linked로_가고_인증을_유지한다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        when(accounts.linkGoogle(7L, SUB))
                .thenThrow(new BusinessException(ErrorCode.GOOGLE_ALREADY_LINKED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, STATE), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_already_linked");
        assertThat(principalOf(session)).isEqualTo(7L);
    }

    @Test
    void state가_다르면_연동하지_않고_google_failed로_가며_임시_상태를_지운다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, "other-state"), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_failed");
        verify(accounts, never()).linkGoogle(anyLong(), anyString());
        assertThat(session.getAttribute(GoogleAccountLinkPending.SESSION_KEY)).isNull();
        assertThat(principalOf(session)).isEqualTo(7L);
    }

    @Test
    void 만료된_연동_상태는_일반_로그인이_아니라_google_failed로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        session.setAttribute(GoogleAccountLinkPending.SESSION_KEY,
                new GoogleAccountLinkPending(7L, STATE, Instant.now().minusSeconds(1)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 7L, STATE), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_failed");
        verify(accounts, never()).linkGoogle(anyLong(), anyString());
        verify(login, never()).decide(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void 콜백_전_인증이_연동_대상과_다르면_연동하지_않는다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(linkingRequest(session, 9L, STATE), response, googleLogin(SUB, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_failed");
        verify(accounts, never()).linkGoogle(anyLong(), anyString());
    }

    @Test
    void 연동_실패는_연동_상태를_지우고_기존_로그인_상태로_mypage에_머문다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        GoogleAccountLinkPending.store(session, 7L, STATE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = linkingRequest(session, 7L, "wrong");

        failure.onAuthenticationFailure(request, response, new SessionAuthenticationException("state"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage?error=google_failed");
        assertThat(session.getAttribute(GoogleAccountLinkPending.SESSION_KEY)).isNull();
        assertThat(principalOf(session)).isEqualTo(7L);
        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void 일반_로그인_실패는_기존처럼_login으로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        failure.onAuthenticationFailure(request, response, new SessionAuthenticationException("state"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_failed");
    }

    @Test
    void 연동_진행_상태는_만료_여부를_읽을_수_있고_지울_수_있다() {
        MockHttpSession session = new MockHttpSession();
        assertThat(GoogleAccountLinkPending.peek(session)).isEmpty();
        assertThat(GoogleAccountLinkPending.peek(null)).isEmpty();

        GoogleAccountLinkPending.store(session, 7L, STATE);
        GoogleAccountLinkPending pending = GoogleAccountLinkPending.peek(session).orElseThrow();
        assertThat(pending.memberId()).isEqualTo(7L);
        assertThat(pending.state()).isEqualTo(STATE);
        assertThat(pending.isExpired(Instant.now())).isFalse();
        assertThat(pending.isExpired(Instant.now().plus(GoogleAccountLinkPending.TTL))).isTrue();

        GoogleAccountLinkPending.clear(session);
        assertThat(GoogleAccountLinkPending.peek(session)).isEmpty();
    }

    @Test
    void 연동_모드에서는_세션_ID를_바꾸지_않고_일반_모드에서는_기존처럼_바꾼다() {
        GoogleLinkAwareSessionAuthenticationStrategy strategy = new GoogleLinkAwareSessionAuthenticationStrategy();

        MockHttpSession linking = new MockHttpSession();
        GoogleAccountLinkPending.store(linking, 7L, STATE);
        MockHttpServletRequest linkRequest = new MockHttpServletRequest();
        linkRequest.setSession(linking);
        String before = linking.getId();
        strategy.onAuthentication(localMember(7L), linkRequest, new MockHttpServletResponse());
        assertThat(linkRequest.getSession(false).getId()).isEqualTo(before);

        MockHttpSession normal = new MockHttpSession();
        MockHttpServletRequest normalRequest = new MockHttpServletRequest();
        normalRequest.setSession(normal);
        String normalBefore = normal.getId();
        strategy.onAuthentication(localMember(7L), normalRequest, new MockHttpServletResponse());
        assertThat(normalRequest.getSession(false).getId()).isNotEqualTo(normalBefore);
    }
}
