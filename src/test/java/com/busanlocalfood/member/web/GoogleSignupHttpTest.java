package com.busanlocalfood.member.web;

import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.busanlocalfood.member.jwt.IssuedAccessToken;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.oauth.GoogleSignupPending;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.AuthApiService.IssuedTokens;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.web.HomeController;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Google 신규 가입 닉네임 설정 화면의 MockMvc 테스트. 가입 서비스와 요청 제한은 목으로 바꾼다. */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthCookies.class, AuthController.class, ScreenAuthController.class,
        ScreenLogoutController.class, GoogleSignupController.class, ScreenAuthAdvice.class, HomeController.class,
        GoogleSignupHttpTest.ClientIpConfig.class})
class GoogleSignupHttpTest {

    @MockitoBean
    private MemberAccountService memberAccountService;

    private static final String JWT_SECRET = randomBase64Key();
    private static final String PAGE = "/signup/google/nickname";
    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private GoogleSignupService signupService;

    @MockitoBean
    private AuthApiService authService;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        GoogleSignupPending.store(session, "sub-http-1", "google.user@example.com");
        return session;
    }

    /** CSRF 토큰을 얻기 위해 세션을 준비한 뒤 폼 페이지를 받는다. */
    private String csrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/login").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Member member(long id) {
        Member member = Member.create("google.user@example.com", null, "구글신규", "sub-http-1");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static IssuedSession issued(Member member) {
        return new IssuedSession(member, new IssuedTokens(
                new IssuedAccessToken("access-raw-value", Instant.now(), Instant.now().plusSeconds(1800)),
                new IssuedRefreshToken("refresh-raw-value", LocalDateTime.now().plusDays(14))));
    }

    @Test
    void 가입_진행_상태가_없으면_닉네임_화면에_갈_수_없고_로그인으로_간다() throws Exception {
        mockMvc.perform(get(PAGE)).andExpect(status().isFound()).andExpect(header().string("Location", "/login"));
    }

    @Test
    void 가입_진행_상태가_있으면_닉네임_입력_폼을_보여준다() throws Exception {
        mockMvc.perform(get(PAGE).session(pendingSession()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("name=\"nickname\"")));
    }

    @Test
    void 진행_상태가_없으면_닉네임_제출은_가입하지_않고_로그인으로_간다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "구글신규"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"));
        verify(signupService, never()).complete(any(), anyString());
    }

    @Test
    void 닉네임_형식이_틀리면_validation_failed로_돌아가고_진행_상태는_남긴다() throws Exception {
        when(signupService.complete(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.NICKNAME_INVALID));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", PAGE + "?error=validation_failed"));
        assertThat(GoogleSignupPending.current(session)).isPresent();
    }

    @Test
    void 닉네임이_중복이면_nickname_duplicated로_돌아가고_진행_상태는_남긴다() throws Exception {
        when(signupService.complete(any(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "중복닉네임"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", PAGE + "?error=nickname_duplicated"));
        assertThat(GoogleSignupPending.current(session)).isPresent();
    }

    @Test
    void 이메일이_그_사이_가입되었으면_계정_존재_안내로_가고_진행_상태를_지운다() throws Exception {
        when(signupService.complete(any(), anyString())).thenThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATED));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "새닉네임"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login?error=email_account_exists"));
        assertThat(GoogleSignupPending.current(session)).isEmpty();
    }

    @Test
    void 가입에_성공하면_화면_세션과_JWT_쿠키를_받고_홈으로_간다() throws Exception {
        when(signupService.complete(any(), anyString())).thenReturn(issued(member(7L)));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        MvcResult result = mockMvc.perform(post(PAGE).session(session).param("_csrf", token)
                        .param("nickname", "구글신규"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        assertThat(session.isInvalid()).isTrue();
        MockHttpSession current = (MockHttpSession) result.getRequest().getSession(false);
        SecurityContext context = (SecurityContext) current.getAttribute(SESSION_CONTEXT);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(7L);
        assertThat(context.getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("access_token=")).anyMatch(h -> h.startsWith("refresh_token="));
        assertThat(GoogleSignupPending.current(current)).isEmpty();
    }

    @Test
    void 만료된_진행_상태로는_제출할_수_없다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleSignupPending.SESSION_KEY,
                new GoogleSignupPending("sub-old", "old@example.com", Instant.now().minusSeconds(1)));
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "구글신규"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"));
        verify(signupService, never()).complete(any(), anyString());
    }

    @Test
    void 닉네임_제출에_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post(PAGE).session(pendingSession()).param("nickname", "구글신규"))
                .andExpect(status().isForbidden());
        verify(signupService, never()).complete(any(), anyString());
    }

    @Test
    void 닉네임_제출은_회원가입과_같은_IP_카운터를_쓰고_제한_초과면_회원을_만들지_않는다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Limited(30));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "구글신규"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
        verify(signupService, never()).complete(any(), anyString());
        assertThat(GoogleSignupPending.current(session)).isPresent();
    }

    @Test
    void 닉네임_제출은_허용되면_한_번만_집계된다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(signupService.complete(any(), anyString())).thenReturn(issued(member(7L)));
        MockHttpSession session = pendingSession();
        String token = csrf(session);

        mockMvc.perform(post(PAGE).session(session).param("_csrf", token).param("nickname", "구글신규"))
                .andExpect(status().isFound());

        verify(rateLimit, times(1)).recordSignupRequest(anyString());
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
