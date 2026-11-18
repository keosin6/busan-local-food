package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.IssuedAccessToken;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenProperties;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.AuthApiService.IssuedTokens;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleLoginService.GoogleLoginDecision;
import com.busanlocalfood.member.web.AuthCookies;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;

/** Google 콜백 성공 처리: 정식 로그인만 화면 세션을 만들고, 나머지는 기존 인증 상태를 복원한다. */
class GoogleOAuth2SuccessHandlerTest {

    private static final String CONTEXT_KEY = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    private final GoogleLoginService login = mock(GoogleLoginService.class);
    private final AuthApiService authApi = mock(AuthApiService.class);
    private final AuthCookies cookies = cookies();
    private final GoogleOAuth2SuccessHandler handler = new GoogleOAuth2SuccessHandler(login, authApi, cookies);

    @Test
    void 연결된_회원은_화면_세션과_JWT_쿠키를_받고_홈으로_간다() throws Exception {
        Member member = member(7L);
        when(login.decide(eq("sub-1"), eq("user@example.com"), eq(true)))
                .thenReturn(new GoogleLoginDecision.Login(member));
        when(authApi.issueSession(member)).thenReturn(session(member));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, google("sub-1", "user@example.com", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(h -> h.startsWith("access_token="))
                .anyMatch(h -> h.startsWith("refresh_token="));
        SecurityContext context = (SecurityContext) request.getSession(false).getAttribute(CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void 신규_가입_대상은_가입_진행_상태만_저장하고_로그인하지_않는다() throws Exception {
        when(login.decide(anyString(), anyString(), eq(true)))
                .thenReturn(new GoogleLoginDecision.NewMember("sub-9", "new@example.com"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, google("sub-9", "new@example.com", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/signup/google/nickname");
        var pending = GoogleSignupPending.current(request.getSession(false));
        assertThat(pending).isPresent();
        assertThat(pending.get().googleSub()).isEqualTo("sub-9");
        assertThat(request.getSession(false).getAttribute(CONTEXT_KEY)).isNull();
        verify(authApi, never()).issueSession(any());
    }

    @Test
    void 신규_가입_대상_처리는_기존_로그인_상태를_그대로_되돌린다() throws Exception {
        UsernamePasswordAuthenticationToken previous = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        when(login.decide(anyString(), anyString(), eq(true)))
                .thenReturn(new GoogleLoginDecision.NewMember("sub-9", "new@example.com"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION, previous);
        request.setSession(sessionHoldingOAuth2Login());

        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
                google("sub-9", "new@example.com", true));

        SecurityContext restored = (SecurityContext) request.getSession(false).getAttribute(CONTEXT_KEY);
        assertThat(restored.getAuthentication().getPrincipal()).isEqualTo(7L);
    }

    @Test
    void 같은_이메일_회원이_있으면_자동_연결하지_않고_안내_오류로_간다() throws Exception {
        when(login.decide(anyString(), anyString(), eq(true))).thenReturn(new GoogleLoginDecision.EmailAccountExists());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, google("sub-2", "user@example.com", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=email_account_exists");
        assertThat(GoogleSignupPending.current(request.getSession(false))).isEmpty();
        verify(authApi, never()).issueSession(any());
    }

    @Test
    void 이메일_미검증은_google_unverified로_가고_세션을_만들지_않는다() throws Exception {
        when(login.decide(anyString(), anyString(), eq(false))).thenReturn(new GoogleLoginDecision.Unverified());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                google("sub-3", "user@example.com", false));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_unverified");
        verify(authApi, never()).issueSession(any());
    }

    @Test
    void Google_인증_정보가_없으면_google_failed로_간다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication notGoogle = new UsernamePasswordAuthenticationToken("someone", null, List.of());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, notGoogle);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_failed");
        verify(login, never()).decide(anyString(), anyString(), eq(true));
    }

    private static OAuth2AuthenticationToken google(String sub, String email, boolean verified) {
        Map<String, Object> attributes = Map.of("sub", sub, "email", email, "email_verified", verified);
        DefaultOAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }

    private static MockHttpSession sessionHoldingOAuth2Login() {
        MockHttpSession session = new MockHttpSession();
        UsernamePasswordAuthenticationToken oauth = new UsernamePasswordAuthenticationToken("google-user", null, List.of());
        session.setAttribute(CONTEXT_KEY, new SecurityContextImpl(oauth));
        return session;
    }

    private static Member member(long id) {
        Member member = Member.create("user@example.com", null, "구글회원", "sub-1");
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static IssuedSession session(Member member) {
        return new IssuedSession(member, new IssuedTokens(
                new IssuedAccessToken("access-raw-value", Instant.now(), Instant.now().plusSeconds(1800)),
                new IssuedRefreshToken("refresh-raw-value", LocalDateTime.now().plusDays(14))));
    }

    private static AuthCookies cookies() {
        Environment environment = mock(Environment.class);
        return new AuthCookies(environment, new RefreshTokenProperties(1209600L));
    }
}
