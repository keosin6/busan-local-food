package com.busanlocalfood.member.web;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.oauth.GoogleAccountLinkPending;
import com.busanlocalfood.member.oauth.GoogleOAuth2Config;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.MemberAccountService.PasswordOutcome;
import com.busanlocalfood.member.service.MemberAccountService.UnlinkOutcome;
import com.busanlocalfood.web.HomeController;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 마이페이지, Google 계정 연결 시작, 해제, 비밀번호 설정과 화면 429 안내의 HTTP 동작 (API_SPEC 2.6, 2.7, 2.7.1, 1.4).
 * 실제 SecurityConfig와 CSRF를 쓴다. 회원·인증·요청 제한 서비스는 목이다. Google 서버와는 통신하지 않는다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthCookies.class, GoogleOAuth2Config.class,
        MyPageController.class, GoogleAccountLinkController.class, ScreenRateLimitAdvice.class,
        RateLimitedPageController.class, ScreenAuthAdvice.class, ScreenAuthController.class,
        ScreenLogoutController.class, GoogleSignupController.class, HomeController.class, RestaurantScreenController.class,
        MyPageHttpTest.ClientIpConfig.class})
class MyPageHttpTest {

    @MockitoBean
    private RestaurantService restaurantService;

    private static final String CLIENT_ID = "test-client-mypage";
    private static final String CLIENT_SECRET = "test-secret-mypage";
    private static final String JWT_SECRET = randomBase64Key();
    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNOP";
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("app.oauth2.google.client-id", () -> CLIENT_ID);
        registry.add("app.oauth2.google.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApiService authService;

    @MockitoBean
    private MemberAccountService accounts;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private GoogleLoginService googleLoginService;

    @MockitoBean
    private GoogleSignupService googleSignupService;

    private Member googleOnly;
    private Member withPassword;

    @BeforeEach
    void setUp() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Allowed());
        googleOnly = member(7L, null, "google-sub-7");
        withPassword = member(8L, PASSWORD_HASH, null);
        when(authService.findMember(7L)).thenReturn(googleOnly);
        when(authService.findMember(8L)).thenReturn(withPassword);
    }

    private static Member member(long id, String passwordHash, String googleSub) {
        Member member = passwordHash == null
                ? Member.create("g@example.com", null, "구글회원", googleSub)
                : Member.create("m@example.com", passwordHash, "회원", googleSub);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        return session;
    }

    /** 로그인된 세션으로 마이페이지를 열어 CSRF 토큰을 얻는다. */
    private String csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/mypage").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("마이페이지 폼의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @Test
    void 미로그인으로_마이페이지에_접근하면_login으로_보낸다() throws Exception {
        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void 로그인하면_마이페이지가_보이고_Google_연결_폼과_CSRF_필드를_포함한다() throws Exception {
        mockMvc.perform(get("/mypage").session(loggedInSession(8L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("action=\"/mypage/google/link\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void 연결_시작은_CSRF가_없으면_거부되고_요청_제한을_세지_않는다() throws Exception {
        mockMvc.perform(post("/mypage/google/link").session(loggedInSession(7L)))
                .andExpect(status().isForbidden());

        verify(rateLimit, never()).recordGoogleOAuthRequest(anyString());
    }

    @Test
    void 연결_시작은_Google_인증_페이지로_보내고_state와_연동_상태를_세션에_저장하며_세션과_쿠키를_유지한다() throws Exception {
        MockHttpSession session = loggedInSession(8L);
        String token = csrf(session);
        String before = session.getId();

        MvcResult result = mockMvc.perform(post("/mypage/google/link").session(session).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "https://accounts.google.com/o/oauth2/v2/auth")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        GoogleAccountLinkPending pending = GoogleAccountLinkPending.peek(session).orElseThrow();
        assertThat(pending.memberId()).isEqualTo(8L);
        assertThat(location).contains("state=" + java.net.URLEncoder.encode(pending.state(),
                java.nio.charset.StandardCharsets.UTF_8));
        // 콜백 경로와 같은 redirect_uri를 써야 Google이 콜백을 이 애플리케이션으로 돌려보낸다.
        assertThat(location).contains("redirect_uri=http://localhost/login/oauth2/code/google");
        assertThat(location).doesNotContain("/authorize/oauth2/code/");
        assertThat(session.getId()).isEqualTo(before);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        verify(rateLimit, times(1)).recordGoogleOAuthRequest(anyString());
    }

    @Test
    void 연결_시작_제한_초과는_429와_Retry_After이고_OAuth2_상태를_만들지_않는다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(120));
        MockHttpSession session = loggedInSession(8L);
        String token = csrf(session);

        MvcResult result = mockMvc.perform(post("/mypage/google/link").session(session).param("_csrf", token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("요청이 너무 많습니다")))
                .andReturn();

        assertThat(GoogleAccountLinkPending.peek(session)).isEmpty();
        assertThat(result.getResponse().getHeader("Location")).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(session.getAttribute(SESSION_CONTEXT)).isNotNull();
    }

    @Test
    void 일반_Google_시작_요청_제한_초과는_화면_안내로_전달되고_429와_Retry_After를_준다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(77));

        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "77"))
                .andExpect(forwardedUrl("/rate-limited"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void 연동_콜백의_state가_틀리면_Spring_검증에서_실패하고_기존_로그인과_연동_상태를_정리한다() throws Exception {
        MockHttpSession session = loggedInSession(8L);
        GoogleAccountLinkPending.store(session, 8L, "expected-state");

        MvcResult result = mockMvc.perform(get("/login/oauth2/code/google").session(session)
                        .param("code", "code-1").param("state", "wrong-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?error=google_failed"))
                .andReturn();

        assertThat(GoogleAccountLinkPending.peek(session)).isEmpty();
        Object context = session.getAttribute(SESSION_CONTEXT);
        assertThat(context).isInstanceOf(SecurityContextImpl.class);
        assertThat(((SecurityContextImpl) context).getAuthentication().getPrincipal()).isEqualTo(8L);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void 안내_페이지를_직접_열면_대기_정보가_없으므로_홈으로_보낸다() throws Exception {
        mockMvc.perform(get("/rate-limited"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 연동_해제_결과에_따라_올바른_오류_코드로_보낸다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrf(session);
        when(accounts.unlinkGoogle(7L, "pw-1")).thenReturn(UnlinkOutcome.UNLINKED);
        when(accounts.unlinkGoogle(7L, "pw-2")).thenReturn(UnlinkOutcome.PASSWORD_REQUIRED);
        when(accounts.unlinkGoogle(7L, "pw-3")).thenReturn(UnlinkOutcome.INVALID_CREDENTIALS);
        when(accounts.unlinkGoogle(7L, "pw-4")).thenReturn(UnlinkOutcome.NOT_LINKED);

        mockMvc.perform(post("/mypage/google/unlink").session(session).param("_csrf", token).param("password", "pw-1"))
                .andExpect(redirectedUrl("/mypage?google=unlinked"));
        mockMvc.perform(post("/mypage/google/unlink").session(session).param("_csrf", token).param("password", "pw-2"))
                .andExpect(redirectedUrl("/mypage?error=password_required"));
        mockMvc.perform(post("/mypage/google/unlink").session(session).param("_csrf", token).param("password", "pw-3"))
                .andExpect(redirectedUrl("/mypage?error=invalid_credentials"));
        mockMvc.perform(post("/mypage/google/unlink").session(session).param("_csrf", token).param("password", "pw-4"))
                .andExpect(redirectedUrl("/mypage?error=google_not_linked"));
    }

    @Test
    void 연동_해제는_CSRF가_없으면_거부된다() throws Exception {
        mockMvc.perform(post("/mypage/google/unlink").session(loggedInSession(7L)).param("password", "pw"))
                .andExpect(status().isForbidden());

        verify(accounts, never()).unlinkGoogle(anyLong(), anyString());
    }

    @Test
    void 이미_비밀번호가_있는_회원은_설정_화면에_들어갈_수_없다() throws Exception {
        mockMvc.perform(get("/mypage/password").session(loggedInSession(8L)))
                .andExpect(redirectedUrl("/mypage?error=password_already_set"));
    }

    @Test
    void Google_전용_회원은_설정_화면을_보고_설정하면_원문_없이_mypage로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        mockMvc.perform(get("/mypage/password").session(session)).andExpect(status().isOk());
        String token = csrf(session);
        when(accounts.setLocalPassword(7L, "Abcdef1!", "Abcdef1!")).thenReturn(PasswordOutcome.SET);

        MvcResult result = mockMvc.perform(post("/mypage/password").session(session).param("_csrf", token)
                        .param("password", "Abcdef1!").param("passwordConfirm", "Abcdef1!"))
                .andExpect(redirectedUrl("/mypage?password=set"))
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("Abcdef1!");
    }

    @Test
    void 비밀번호_설정_검증_실패와_이미_설정됨은_각각_다른_경로로_간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrf(session);
        when(accounts.setLocalPassword(7L, "weak", "weak")).thenReturn(PasswordOutcome.INVALID);
        when(accounts.setLocalPassword(7L, "Abcdef1!", "Abcdef1!")).thenReturn(PasswordOutcome.ALREADY_SET);

        mockMvc.perform(post("/mypage/password").session(session).param("_csrf", token)
                        .param("password", "weak").param("passwordConfirm", "weak"))
                .andExpect(redirectedUrl("/mypage/password?error=validation_failed"));
        mockMvc.perform(post("/mypage/password").session(session).param("_csrf", token)
                        .param("password", "Abcdef1!").param("passwordConfirm", "Abcdef1!"))
                .andExpect(redirectedUrl("/mypage?error=password_already_set"));
    }

    @Test
    void 비밀번호_설정_제출은_CSRF가_없으면_거부된다() throws Exception {
        mockMvc.perform(post("/mypage/password").session(loggedInSession(7L))
                        .param("password", "Abcdef1!").param("passwordConfirm", "Abcdef1!"))
                .andExpect(status().isForbidden());

        verify(accounts, never()).setLocalPassword(anyLong(), anyString(), anyString());
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
