package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.web.HomeController;
import java.security.SecureRandom;
import java.util.Base64;
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
 * 화면 체인의 보호 경로 (API_SPEC 10, 맛집 등록·수정 화면 F1-01 보호).
 * 실제 SecurityConfig와 CSRF, 세션을 쓴다. 회원·인증·요청 제한 서비스는 목이다.
 * 맛집 컨트롤러는 아직 없으므로 인증을 통과한 요청은 404가 된다. 이 404는 보안 필터를 통과했다는 뜻으로 본다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthCookies.class, MyPageController.class,
        ScreenAuthController.class, ScreenLogoutController.class, ScreenAuthAdvice.class,
        HomeController.class, ProtectedScreenHttpTest.ClientIpConfig.class})
class ProtectedScreenHttpTest {

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String LOGIN_LOCATION = "http://localhost/login";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> randomBase64Key());
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

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

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.create("protected@example.com", "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNOP",
                "보호회원", null);
        ReflectionTestUtils.setField(member, "id", 7L);
        when(authService.findMember(7L)).thenReturn(member);
    }

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        return session;
    }

    /** 세션을 만들고 그 세션의 CSRF 토큰을 얻는다. 로그인 화면은 로그인 상태와 관계없이 열린다. */
    private String csrf(MockHttpSession session) throws Exception {
        MvcResult page = mockMvc.perform(get("/login").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("로그인 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    /** 보안 필터를 통과했는지 확인한다. 맛집 핸들러가 없으므로 404가 정상 통과 결과다. */
    private static void assertPassedSecurity(MvcResult result) {
        int code = result.getResponse().getStatus();
        assertThat(code).as("보안 필터 통과 후 핸들러 부재 404").isEqualTo(404);
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }

    // ---- 1~2. 비로그인 GET은 /login으로 보낸다 ----

    @Test
    void 비로그인_GET_맛집_등록_화면은_login으로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/restaurants/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    @Test
    void 비로그인_GET_맛집_수정_화면은_login으로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/restaurants/1/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    // ---- 3~4. 로그인 세션 GET은 인증 단계를 통과한다 ----

    @Test
    void 로그인_세션_GET_맛집_등록_화면은_인증을_통과한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/restaurants/new").session(loggedInSession(7L))).andReturn();

        assertPassedSecurity(result);
    }

    @Test
    void 로그인_세션_GET_맛집_수정_화면은_인증을_통과한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/restaurants/1/edit").session(loggedInSession(7L))).andReturn();

        assertPassedSecurity(result);
    }

    // ---- 5~6. 비로그인 POST는 유효한 CSRF가 있어도 /login으로 보낸다 ----

    @Test
    void 비로그인_POST_맛집_등록은_유효한_CSRF가_있어도_login으로_리다이렉트한다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);

        mockMvc.perform(post("/restaurants/new").session(session).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    @Test
    void 비로그인_POST_맛집_수정은_유효한_CSRF가_있어도_login으로_리다이렉트한다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = csrf(session);

        mockMvc.perform(post("/restaurants/1/edit").session(session).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    // ---- 7. 로그인 세션의 POST는 CSRF 토큰이 없으면 403 ----

    @Test
    void 로그인_세션_POST_맛집_등록은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/new").session(loggedInSession(7L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인_세션_POST_맛집_수정은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/1/edit").session(loggedInSession(7L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 로그인_세션_POST_맛집_등록은_CSRF_토큰이_있으면_인증을_통과한다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrf(session);

        MvcResult result = mockMvc.perform(post("/restaurants/new").session(session).param("_csrf", token)).andReturn();

        assertPassedSecurity(result);
    }

    // ---- JWT 쿠키만으로는 화면 인증되지 않는다 ----

    @Test
    void 유효한_JWT_쿠키만_있으면_맛집_등록_화면은_login으로_리다이렉트한다() throws Exception {
        String jwt = accessTokenIssuer.issue(7L, Role.USER).token();
        mockMvc.perform(get("/restaurants/new").cookie(new jakarta.servlet.http.Cookie("access_token", jwt)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    // ---- 8. 기존 보호 경로 유지 ----

    @Test
    void 비로그인_GET_마이페이지는_login으로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(LOGIN_LOCATION));
    }

    @Test
    void 로그인_세션_GET_마이페이지는_인증을_통과한다() throws Exception {
        mockMvc.perform(get("/mypage").session(loggedInSession(7L)))
                .andExpect(status().isOk());
    }

    // ---- 9. 공개 경로와 API 인증 회귀 ----

    @Test
    void 공개_홈은_비로그인으로_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void 공개_로그인_화면은_비로그인으로_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void API_인증_경로는_화면_세션으로_인증되지_않는다() throws Exception {
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
