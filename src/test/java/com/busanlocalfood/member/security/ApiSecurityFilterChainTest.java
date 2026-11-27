package com.busanlocalfood.member.security;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenAuthenticationFilter;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * 실제 SecurityConfig 체인과 실제 JwtConfig(AccessTokenVerifier)를 통과하는 MockMvc 통합 테스트.
 * API 체인: JWT만 인증 근거. 화면 세션은 무시. 공개 경로는 API_SPEC 2.1~2.4 네 개만.
 * 화면 체인: 세션 인증 유지. JWT는 인증 근거가 아님. 요청 제한 기존 동작 유지.
 * 요청 제한 서비스는 목으로 바꾼다. 검증 대상은 체인 배치와 인증 상태다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        ApiSecurityFilterChainTest.ClientIpConfig.class, ApiSecurityFilterChainTest.Probes.class})
class ApiSecurityFilterChainTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.favorite.service.FavoriteService favoriteService;

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewSummaryService reviewSummaryService;

    @MockitoBean
    private com.busanlocalfood.image.service.RestaurantImageService restaurantImageService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    private static final String JWT_SECRET = randomBase64Key();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static Cookie jwtCookie(String token) {
        return new Cookie("access_token", token);
    }

    /** API 체인의 CSRF 쿠키를 받는다. 인증 전 GET도 CsrfCookieFilter가 쿠키를 내려준다. */
    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private String validToken(Role role) {
        return issuer.issue(7L, role).token();
    }

    private String tokenSignedWith(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        JwtEncoder other = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
        return new AccessTokenIssuer(other, Duration.ofMinutes(30), Clock.systemUTC()).issue(7L, Role.USER).token();
    }

    @Test
    void API_JWT가_정상이면_회원_ID와_ROLE로_인증된다() throws Exception {
        mockMvc.perform(get("/api/me").cookie(jwtCookie(validToken(Role.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string("7|ROLE_USER"));
    }

    @Test
    void API_JWT의_ADMIN_권한이_그대로_반영된다() throws Exception {
        mockMvc.perform(get("/api/me").cookie(jwtCookie(validToken(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string("7|ROLE_ADMIN"));
    }

    @Test
    void JWT_쿠키가_없는_API_요청은_401_TOKEN_INVALID다() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void 만료된_JWT는_401이다() throws Exception {
        AccessTokenIssuer expired = new AccessTokenIssuer(jwtEncoder, Duration.ofMinutes(1),
                Clock.fixed(Instant.now().minus(2, ChronoUnit.HOURS), java.time.ZoneId.of("Asia/Seoul")));
        String token = expired.issue(7L, Role.USER).token();

        mockMvc.perform(get("/api/me").cookie(jwtCookie(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 위조된_JWT는_401이다() throws Exception {
        String forged = tokenSignedWith(randomBase64Key());

        mockMvc.perform(get("/api/me").cookie(jwtCookie(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 형식이_잘못된_JWT는_401이다() throws Exception {
        mockMvc.perform(get("/api/me").cookie(jwtCookie("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 화면_세션만_있는_API_요청은_인증되지_않는다() throws Exception {
        MockHttpSession session = screenSession("screen-user");

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void JWT만_있는_화면_요청은_인증_상태가_되지_않는다() throws Exception {
        mockMvc.perform(get("/screen/whoami").cookie(jwtCookie(validToken(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    void 정상_API_JWT_요청은_세션을_만들지_않으므로_JWT_인증이_화면에_넘어가지_않는다() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/me").cookie(jwtCookie(validToken(Role.ADMIN))))
                .andExpect(status().isOk()).andReturn();

        assertThat(api.getRequest().getSession(false)).isNull();
        assertThat(api.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    void 화면_세션_인증은_화면_요청에서_그대로_유지된다() throws Exception {
        mockMvc.perform(get("/screen/whoami").session(screenSession("screen-user")))
                .andExpect(status().isOk())
                .andExpect(content().string("screen-user"));
    }

    @Test
    void 화면_세션과_JWT가_모두_있으면_각_체인은_자기_인증_수단만_쓴다() throws Exception {
        MockHttpSession session = screenSession("screen-user");

        mockMvc.perform(get("/screen/whoami").session(session).cookie(jwtCookie(validToken(Role.USER))))
                .andExpect(content().string("screen-user"));
        mockMvc.perform(get("/api/me").session(session).cookie(jwtCookie(validToken(Role.USER))))
                .andExpect(content().string("7|ROLE_USER"));
    }

    @Test
    void API_상태_변경_요청은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/api/me").cookie(jwtCookie(validToken(Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void API_상태_변경_요청은_XSRF_쿠키와_헤더가_맞으면_통과한다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/me").cookie(jwtCookie(validToken(Role.USER)), xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void 공개_인증_경로는_JWT_없이_POST할_수_있다() throws Exception {
        Cookie xsrf = xsrfCookie();
        for (String path : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")) {
            mockMvc.perform(post(path).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 공개_목록에_없는_API_POST는_JWT_없이_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/other").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_경로라도_GET은_공개되지_않는다() throws Exception {
        mockMvc.perform(get("/api/auth/signup")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_응답에는_access_token_쿠키가_설정되지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me")).andReturn();

        assertThat(result.getResponse().getCookie("access_token")).isNull();
    }

    @Test
    void API_회원가입_집계는_JWT_없이도_실행되고_제한_초과는_429다() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Limited(42));
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void API_회원가입_CSRF_실패는_집계하지_않는다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(rateLimitService, never()).recordSignupRequest(any());
    }

    @Test
    void API_회원가입은_CSRF_통과_후_한_번만_집계된다() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Allowed());
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(rateLimitService, times(1)).recordSignupRequest("127.0.0.1");
    }

    private String tokenFor(long memberId, Role role) {
        return issuer.issue(memberId, role).token();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- Bearer 헤더 인증 (API_SPEC 1.0) ----

    @Test
    void 정상_Bearer_JWT는_인증에_성공한다() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(validToken(Role.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string("7|ROLE_USER"));
    }

    @Test
    void Bearer_JWT의_회원_ID와_ADMIN_권한이_반영된다() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(tokenFor(42L, Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string("42|ROLE_ADMIN"));
    }

    @Test
    void 잘못된_Bearer_JWT는_401이다() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 만료된_Bearer_JWT는_401이다() throws Exception {
        AccessTokenIssuer expired = new AccessTokenIssuer(jwtEncoder, Duration.ofMinutes(1),
                Clock.fixed(Instant.now().minus(2, ChronoUnit.HOURS), java.time.ZoneId.of("Asia/Seoul")));

        mockMvc.perform(get("/api/me").header("Authorization", bearer(expired.issue(7L, Role.USER).token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Bearer_헤더가_있으면_쿠키보다_우선한다() throws Exception {
        // 쿠키는 회원 9, Bearer는 회원 7이다. 인증 주체는 Bearer의 회원이어야 한다.
        mockMvc.perform(get("/api/me").cookie(jwtCookie(tokenFor(9L, Role.ADMIN)))
                        .header("Authorization", bearer(tokenFor(7L, Role.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string("7|ROLE_USER"));
    }

    @Test
    void 잘못된_Bearer_헤더가_있으면_유효한_쿠키로_우회_인증되지_않는다() throws Exception {
        String validCookie = validToken(Role.ADMIN);
        for (String header : List.of("Bearer ", "Bearer not a jwt", "Basic dXNlcjpwYXNz", "Bearer")) {
            mockMvc.perform(get("/api/me").cookie(jwtCookie(validCookie)).header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void Bearer_스킴은_대소문자를_구분하지_않는다() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "bearer " + validToken(Role.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void Bearer_인증_상태_변경_요청은_CSRF_헤더_없이_통과한다() throws Exception {
        mockMvc.perform(post("/api/me").header("Authorization", bearer(validToken(Role.USER))))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠키_인증_상태_변경_요청은_올바른_CSRF_헤더가_있으면_통과한다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/me").cookie(jwtCookie(validToken(Role.USER)), xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠키_인증_상태_변경_요청은_틀린_CSRF_헤더면_403이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/me").cookie(jwtCookie(validToken(Role.USER)), xsrf)
                        .header("X-XSRF-TOKEN", "wrong-value"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 인증_없는_상태_변경_요청도_CSRF가_인증보다_먼저_판정되어_403이다() throws Exception {
        // CSRF 필터가 JWT 필터보다 먼저 실행된다. 쿠키가 없는 익명 POST도 CSRF 헤더가 없으면 403이다.
        mockMvc.perform(post("/api/other")).andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_Bearer_상태_변경_요청은_CSRF_대상이_아니므로_403이_아니라_401이다() throws Exception {
        mockMvc.perform(post("/api/me").header("Authorization", bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Bearer_스킴은_대소문자와_무관하게_상태_변경_CSRF_면제다() throws Exception {
        mockMvc.perform(post("/api/me").header("Authorization", "bEARER " + validToken(Role.USER)))
                .andExpect(status().isOk());
    }

    // ---- CSRF 면제는 Bearer 스킴에만 적용된다 (PR #19 보완) ----

    @Test
    void Basic_헤더의_상태_변경_요청은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/api/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isForbidden());
    }

    @Test
    void Basic_헤더의_상태_변경_요청도_올바른_CSRF_토큰이면_CSRF를_통과하고_인증_정책을_받는다() throws Exception {
        // CSRF는 통과한다. Basic은 Bearer가 아니므로 JWT로 인증되지 않아 인증 정책에서 401이 된다.
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/me").header("Authorization", "Basic dXNlcjpwYXNz")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Basic_헤더와_유효한_JWT_쿠키가_있어도_쿠키로_우회_인증되지_않고_CSRF가_적용된다() throws Exception {
        mockMvc.perform(post("/api/me").header("Authorization", "Basic dXNlcjpwYXNz")
                        .cookie(jwtCookie(validToken(Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 헤더_없는_익명_상태_변경_요청은_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/api/me")).andExpect(status().isForbidden());
    }

    @Test
    void 공개_인증_경로도_Basic_헤더로는_CSRF를_우회하지_못한다() throws Exception {
        for (String path : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/signup")) {
            mockMvc.perform(post(path).header("Authorization", "Basic dXNlcjpwYXNz")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void Bearer_구분자가_없는_헤더는_Bearer_면제가_아니므로_CSRF가_적용된다() throws Exception {
        // "Bearer" 뒤에 공백이 없으면 Bearer 스킴이 아니다. 토큰이 없는 익명 요청으로 보고 CSRF를 검사한다.
        mockMvc.perform(post("/api/me").header("Authorization", "Bearer" + validToken(Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void Bearer_면제_요청은_쿠키_인증_요청과_구분되어_쿠키를_보지_않는다() throws Exception {
        // 면제되는 Bearer 요청에 쿠키가 있어도 쿠키 토큰은 쓰이지 않는다. 잘못된 Bearer는 401이어야 한다.
        mockMvc.perform(post("/api/me").header("Authorization", bearer("not-a-jwt"))
                        .cookie(jwtCookie(validToken(Role.ADMIN))))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession screenSession(String username) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        return session;
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }

    /** 테스트 전용 엔드포인트. 실제 API 컨트롤러가 아니다. 체인이 요청을 어떻게 다루는지만 보여 준다. */
    @RestController
    static class Probes {

        @GetMapping("/api/me")
        String me(Authentication authentication) {
            return authentication.getPrincipal() + "|" + authentication.getAuthorities().iterator().next().getAuthority();
        }

        @PostMapping("/api/me")
        String meWrite() {
            return "ok";
        }

        @PostMapping({"/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/other", "/api/auth/signup"})
        String publicOrProtectedPost() {
            return "ok";
        }

        @GetMapping("/api/auth/signup")
        String signupGet() {
            return "ok";
        }

        @GetMapping("/screen/whoami")
        String whoami(Authentication authentication) {
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                return "anonymous";
            }
            return authentication.getName();
        }
    }
}
