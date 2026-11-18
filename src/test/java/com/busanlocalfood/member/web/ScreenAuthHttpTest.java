package com.busanlocalfood.member.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.LoginAttemptResult;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.MemberLoginService;
import com.busanlocalfood.member.service.MemberRegistrationService;
import com.busanlocalfood.web.HomeController;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.HttpHeaders;

/**
 * 화면 회원가입·로그인·로그아웃의 MockMvc 테스트. 실제 SecurityConfig(화면 체인, API 체인), 실제 쿠키 정책을 통과한다.
 * 회원·인증·요청 제한 서비스는 목으로 바꾼다. 세션과 CSRF 토큰은 렌더링된 HTML과 MockHttpSession에서 읽는다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthApiService.class, AuthCookies.class, AuthController.class,
        ScreenAuthController.class, ScreenAuthAdvice.class, HomeController.class,
        ScreenAuthHttpTest.ClientIpConfig.class})
class ScreenAuthHttpTest {

    private static final String JWT_SECRET = randomBase64Key();
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final String VALID_PASSWORD = "Passw0rd!";

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private MemberRegistrationService registration;

    @MockitoBean
    private MemberLoginService loginService;

    @MockitoBean
    private RefreshTokenService refreshTokens;

    @MockitoBean
    private MemberRepository members;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    // ---- 픽스처 ----

    private static Member userMember() {
        Member member = Member.create("user@example.com", "$2a$hash", "부산먹보", null);
        ReflectionTestUtils.setField(member, "id", 7L);
        return member;
    }

    private static IssuedRefreshToken refresh(String raw) {
        return new IssuedRefreshToken(raw, LocalDateTime.now().plusDays(14));
    }

    private void stubSignupSuccess() {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(anyLong())).thenReturn(refresh("screen-refresh-raw"));
    }

    private void stubLoginSuccess() {
        when(rateLimit.attemptLogin(anyString(), anyString(), any())).thenAnswer(invocation -> {
            BooleanSupplier credentials = invocation.getArgument(2);
            return credentials.getAsBoolean()
                    ? new LoginAttemptResult.Authenticated()
                    : new LoginAttemptResult.InvalidCredentials();
        });
        when(loginService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.of(userMember()));
        when(refreshTokens.issueForNewLogin(anyLong())).thenReturn(refresh("screen-refresh-raw"));
    }

    private void stubLoginFailure() {
        when(rateLimit.attemptLogin(anyString(), anyString(), any())).thenAnswer(invocation -> {
            BooleanSupplier credentials = invocation.getArgument(2);
            return credentials.getAsBoolean()
                    ? new LoginAttemptResult.Authenticated()
                    : new LoginAttemptResult.InvalidCredentials();
        });
        when(loginService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.empty());
    }

    /** 화면 폼을 받아 세션과 CSRF 토큰을 얻는다. */
    private record Screen(MockHttpSession session, String csrf) {
    }

    private Screen screen(String path, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(path).session(session == null ? new MockHttpSession() : session))
                .andReturn();
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).as("화면 폼의 CSRF 필드").isTrue();
        return new Screen((MockHttpSession) result.getRequest().getSession(false), matcher.group(1));
    }

    private static String contextPrincipal(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(SESSION_CONTEXT);
        return context == null ? null : String.valueOf(context.getAuthentication().getPrincipal());
    }

    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    private static String cookieValue(MvcResult result, String name) {
        String header = setCookie(result, name);
        assertThat(header).as("Set-Cookie " + name).isNotNull();
        int end = header.indexOf(';');
        return header.substring(name.length() + 1, end < 0 ? header.length() : end);
    }

    private Cookie xsrf() throws Exception {
        return mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // ---- 화면 회원가입 POST /signup ----

    @Test
    void 화면_회원가입_성공은_회원을_만들고_화면_세션과_JWT_쿠키를_발급한다() throws Exception {
        stubSignupSuccess();
        Screen page = screen("/signup", null);

        MvcResult result = mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD)
                        .param("passwordConfirm", VALID_PASSWORD).param("nickname", "부산먹보"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        verify(registration).register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보");
        assertThat(contextPrincipal(result.getRequest().getSession(false) instanceof MockHttpSession s ? s : null))
                .isEqualTo("7");
        assertThat(cookieValue(result, "access_token")).isNotBlank();
        assertThat(cookieValue(result, "refresh_token")).isEqualTo("screen-refresh-raw");
    }

    @Test
    void 화면_회원가입_검증_실패는_회원을_만들지_않고_오류_값으로_돌아가며_세션을_만들지_않는다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_INVALID));
        Screen page = screen("/signup", null);

        MvcResult result = mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", "short")
                        .param("passwordConfirm", "short").param("nickname", "부산먹보"))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/signup?error=validation_failed");
        assertThat(contextPrincipal(page.session())).isNull();
        assertThat(setCookie(result, "access_token")).isNull();
        verify(refreshTokens, never()).issueForNewLogin(anyLong());
    }

    @Test
    void 화면_회원가입_중복_이메일과_닉네임은_각각_오류_값으로_돌아간다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATED))
                .thenThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED));
        Screen page = screen("/signup", null);

        MvcResult email = mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD)
                        .param("passwordConfirm", VALID_PASSWORD).param("nickname", "부산먹보"))
                .andReturn();
        MvcResult nickname = mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "other@example.com").param("password", VALID_PASSWORD)
                        .param("passwordConfirm", VALID_PASSWORD).param("nickname", "부산먹보"))
                .andReturn();

        assertThat(email.getResponse().getRedirectedUrl()).isEqualTo("/signup?error=email_duplicated");
        assertThat(nickname.getResponse().getRedirectedUrl()).isEqualTo("/signup?error=nickname_duplicated");
    }

    @Test
    void 화면_회원가입_제한_초과는_429와_Retry_After이고_회원을_만들지_않으며_중복_집계하지_않는다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Limited(30));
        Screen page = screen("/signup", null);

        mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD)
                        .param("passwordConfirm", VALID_PASSWORD).param("nickname", "부산먹보"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));

        verify(registration, never()).register(any(), any(), any(), any());
        verify(rateLimit, times(1)).recordSignupRequest(anyString());
    }

    @Test
    void 화면_회원가입_CSRF_토큰이_없으면_403이고_회원을_만들지_않는다() throws Exception {
        mockMvc.perform(post("/signup").param("email", "user@example.com").param("password", VALID_PASSWORD)
                        .param("passwordConfirm", VALID_PASSWORD).param("nickname", "부산먹보"))
                .andExpect(status().isForbidden());

        verify(registration, never()).register(any(), any(), any(), any());
    }

    // ---- 화면 로그인 POST /login ----

    @Test
    void 화면_로그인_성공은_세션과_JWT_쿠키를_만들고_홈으로_보낸다() throws Exception {
        stubLoginSuccess();
        Screen page = screen("/login", null);

        MvcResult result = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        assertThat(contextPrincipal(result.getRequest().getSession(false) instanceof MockHttpSession s ? s : null))
                .isEqualTo("7");
        assertThat(cookieValue(result, "access_token")).isNotBlank();
        assertThat(cookieValue(result, "refresh_token")).isEqualTo("screen-refresh-raw");
    }

    @Test
    void 화면_로그인_성공_시_로그인_전_세션을_버려_세션_고정을_막는다() throws Exception {
        stubLoginSuccess();
        Screen page = screen("/login", null);
        page.session().setAttribute("planted", "attacker-value");

        MvcResult result = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(page.session().isInvalid()).isTrue();
        MockHttpSession current = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(current).isNotSameAs(page.session());
        assertThat(current.getAttribute("planted")).isNull();
    }

    @Test
    void 화면_로그인_실패는_invalid_credentials로_리다이렉트하고_세션과_쿠키를_만들지_않는다() throws Exception {
        stubLoginFailure();
        Screen page = screen("/login", null);

        MvcResult result = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", "Wrong0000!"))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=invalid_credentials");
        assertThat(contextPrincipal(page.session())).isNull();
        assertThat(setCookie(result, "access_token")).isNull();
    }

    @Test
    void Google_전용_계정_로그인_실패는_일반_실패와_같은_결과다() throws Exception {
        stubLoginFailure();
        Screen page = screen("/login", null);

        String unknown = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "nobody@example.com").param("password", VALID_PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();
        String googleOnly = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "google-only@example.com").param("password", VALID_PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(googleOnly).isEqualTo(unknown).isEqualTo("/login?error=invalid_credentials");
    }

    @Test
    void 화면_로그인_입력_형식_오류는_validation_failed로_돌아가고_제한_판정을_하지_않는다() throws Exception {
        Screen page = screen("/login", null);

        MvcResult result = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "not-an-email").param("password", VALID_PASSWORD))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=validation_failed");
        verify(rateLimit, never()).attemptLogin(anyString(), anyString(), any());
    }

    @Test
    void 화면_로그인_제한_초과는_429와_Retry_After이고_세션과_쿠키를_건드리지_않는다() throws Exception {
        when(rateLimit.attemptLogin(anyString(), anyString(), any()))
                .thenReturn(new LoginAttemptResult.Limited(42));
        Screen page = screen("/login", null);

        MvcResult result = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andReturn();

        assertThat(contextPrincipal(page.session())).isNull();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(loginService, never()).verifyCredentials(anyString(), anyString());
    }

    @Test
    void 화면_로그인_CSRF_토큰이_없으면_403이다() throws Exception {
        stubLoginSuccess();

        mockMvc.perform(post("/login").param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void 화면_로그인_후_API_JWT로_내_정보를_조회할_수_있다() throws Exception {
        stubLoginSuccess();
        when(members.findById(7L)).thenReturn(Optional.of(userMember()));
        Screen page = screen("/login", null);

        MvcResult login = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        String access = cookieValue(login, "access_token");

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk());
    }

    @Test
    void 화면_세션만_있는_API_요청은_인증되지_않아_401이다() throws Exception {
        stubLoginSuccess();
        Screen page = screen("/login", null);
        MvcResult login = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void JWT_쿠키만_있으면_화면에서_로그인_상태가_아니다() throws Exception {
        String token = issuer.issue(7L, Role.USER).token();

        mockMvc.perform(get("/").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(contentNotContaining("id=\"logout-form\""))
                .andExpect(contentContaining("href=\"/login\""));
    }

    @Test
    void 화면_세션이_있으면_화면에_로그아웃_폼이_보인다() throws Exception {
        stubLoginSuccess();
        Screen page = screen("/login", null);
        MvcResult login = mockMvc.perform(post("/login").session(page.session()).param("_csrf", page.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult home = mockMvc.perform(get("/").session(session)).andExpect(status().isOk()).andReturn();
        assertThat(home.getResponse().getContentAsString()).contains("id=\"logout-form\"");
    }

    // ---- 화면 로그아웃 POST /logout (T12 서버 측) ----

    @Test
    void 화면_로그아웃은_세션을_무효화하고_JSESSIONID만_만료시키며_JWT_쿠키를_건드리지_않는다() throws Exception {
        stubLoginSuccess();
        Screen login = screen("/login", null);
        MvcResult signedIn = mockMvc.perform(post("/login").session(login.session()).param("_csrf", login.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);
        Screen home = screen("/", session);

        MvcResult result = mockMvc.perform(post("/logout").session(session).param("_csrf", home.csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"))
                .andReturn();

        assertThat(session.isInvalid()).isTrue();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("JSESSIONID=") && h.contains("Max-Age=0"))
                .noneMatch(h -> h.startsWith("access_token=") || h.startsWith("refresh_token="));
        verify(refreshTokens, never()).logout(anyString());
    }

    @Test
    void 세션이_없는_화면_로그아웃도_로그인으로_보낸다() throws Exception {
        Screen page = screen("/login", null);

        mockMvc.perform(post("/logout").session(page.session()).param("_csrf", page.csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void 화면_로그아웃_CSRF_토큰이_없으면_403이고_세션을_유지한다() throws Exception {
        stubLoginSuccess();
        Screen login = screen("/login", null);
        MvcResult signedIn = mockMvc.perform(post("/login").session(login.session()).param("_csrf", login.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
        assertThat(contextPrincipal(session)).isEqualTo("7");
    }

    @Test
    void API_로그아웃이_실패하면_화면_세션은_유지되고_재시도할_수_있다() throws Exception {
        stubLoginSuccess();
        Screen login = screen("/login", null);
        MvcResult signedIn = mockMvc.perform(post("/login").session(login.session()).param("_csrf", login.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);

        // 1) API 로그아웃 실패 (XSRF 헤더 없음). 화면 로그아웃을 호출하지 않으므로 세션은 그대로다.
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "screen-refresh-raw")))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        assertThat(contextPrincipal(session)).isEqualTo("7");

        // 2) 재시도: API 로그아웃 성공 뒤 화면 로그아웃을 호출한다.
        Cookie xsrf = xsrf();
        mockMvc.perform(post("/api/auth/logout").cookie(xsrf, new Cookie("refresh_token", "screen-refresh-raw"))
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        verify(refreshTokens).logout("screen-refresh-raw");
    }

    @Test
    void API_로그아웃_성공_후_화면_로그아웃이_실패해도_화면_로그아웃을_재시도할_수_있다() throws Exception {
        stubLoginSuccess();
        Screen login = screen("/login", null);
        MvcResult signedIn = mockMvc.perform(post("/login").session(login.session()).param("_csrf", login.csrf())
                        .param("email", "user@example.com").param("password", VALID_PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) signedIn.getRequest().getSession(false);

        Cookie xsrf = xsrf();
        mockMvc.perform(post("/api/auth/logout").cookie(xsrf, new Cookie("refresh_token", "screen-refresh-raw"))
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());

        // 화면 로그아웃 1차 실패 (CSRF 토큰 없음). API 토큰은 이미 폐기되었다.
        mockMvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();

        // 재시도 성공.
        Screen home = screen("/", session);
        mockMvc.perform(post("/logout").session(session).param("_csrf", home.csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"));
        assertThat(session.isInvalid()).isTrue();
    }

    // ---- 도우미 매처 ----

    private static org.springframework.test.web.servlet.ResultMatcher contentContaining(String text) {
        return result -> assertThat(result.getResponse().getContentAsString()).contains(text);
    }

    private static org.springframework.test.web.servlet.ResultMatcher contentNotContaining(String text) {
        return result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(text);
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
