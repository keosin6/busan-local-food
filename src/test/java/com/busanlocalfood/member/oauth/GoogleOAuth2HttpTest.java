package com.busanlocalfood.member.oauth;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.web.AuthController;
import com.busanlocalfood.member.web.AuthCookies;
import com.busanlocalfood.member.web.GoogleSignupController;
import com.busanlocalfood.member.web.ScreenAuthAdvice;
import com.busanlocalfood.member.web.ScreenAuthController;
import com.busanlocalfood.member.web.ScreenLogoutController;
import com.busanlocalfood.web.HomeController;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Google 로그인 시작과 콜백의 HTTP 동작. 실제 SecurityConfig와 Spring OAuth2 필터를 쓴다.
 * 실제 Google 서버와 통신하지 않는다. 시작은 Google 인증 페이지 리다이렉트까지, 콜백은 state 검증 실패와 요청 제한까지 확인한다.
 * 설정 값은 테스트 실행마다 만드는 임의 문자열이다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthCookies.class, GoogleOAuth2Config.class,
        AuthController.class, ScreenAuthController.class, ScreenLogoutController.class,
        GoogleSignupController.class, ScreenAuthAdvice.class, HomeController.class, RestaurantScreenController.class,
        GoogleOAuth2HttpTest.ClientIpConfig.class})
class GoogleOAuth2HttpTest {

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.image.service.RestaurantImageService restaurantImageService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    static final String CLIENT_ID = "test-client-" + randomToken();
    private static final String CLIENT_SECRET = "test-secret-" + randomToken();
    private static final String JWT_SECRET = randomBase64Key();
    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final String START = "/oauth2/authorization/google";
    private static final String CALLBACK = "/login/oauth2/code/google";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("app.oauth2.google.client-id", () -> CLIENT_ID);
        registry.add("app.oauth2.google.client-secret", () -> CLIENT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private GoogleLoginService googleLoginService;

    @MockitoBean
    private GoogleSignupService googleSignupService;

    @MockitoBean
    private AuthApiService authService;

    @BeforeEach
    void allowByDefault() {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Allowed());
    }

    private static String randomToken() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.USER.name())));
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(auth));
        return session;
    }

    private static Object principalOf(MockHttpSession session) {
        return ((org.springframework.security.core.context.SecurityContext) session.getAttribute(SESSION_CONTEXT))
                .getAuthentication().getPrincipal();
    }

    @Test
    void 시작_요청은_Google_인증_페이지로_리다이렉트하고_client_id와_state를_담는다() throws Exception {
        MvcResult result = mockMvc.perform(get(START))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith("https://accounts.google.com/").contains("client_id=" + CLIENT_ID)
                .contains("state=").contains("scope=")
                .contains("redirect_uri=http://localhost/login/oauth2/code/google");
    }

    @Test
    void 시작_요청_제한_초과는_429와_Retry_After이고_Google로_리다이렉트하지_않는다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(120));

        mockMvc.perform(get(START))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void 시작_요청은_기존_로그인_상태를_바꾸지_않는다() throws Exception {
        MockHttpSession session = loggedInSession(7L);

        mockMvc.perform(get(START).session(session)).andExpect(status().isFound());

        assertThat(principalOf(session)).isEqualTo(7L);
    }

    @Test
    void 잘못된_state로_콜백하면_google_failed로_가고_기존_로그인_상태를_유지한다() throws Exception {
        MockHttpSession session = loggedInSession(7L);

        MvcResult result = mockMvc.perform(get(CALLBACK).session(session)
                        .param("code", "forged-code").param("state", "forged-state"))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=google_failed");
        assertThat(principalOf(session)).isEqualTo(7L);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 콜백_요청_제한_초과는_429이고_기존_로그인_세션과_JWT를_유지한다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(60));
        MockHttpSession session = loggedInSession(7L);

        MvcResult result = mockMvc.perform(get(CALLBACK).session(session)
                        .param("code", "forged-code").param("state", "forged-state"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andReturn();

        assertThat(principalOf(session)).isEqualTo(7L);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(googleLoginService, never()).decide(any(), any(), anyBoolean());
    }

    @Test
    void 로그인_화면에는_Google_로그인_버튼이_보인다() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("href=\"/oauth2/authorization/google\"")));
    }

    @Test
    void Google_설정이_있어도_API_인증은_그대로_JWT만_쓴다() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(loggedInSession(7L)))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
