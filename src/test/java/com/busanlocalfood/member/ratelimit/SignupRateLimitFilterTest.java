package com.busanlocalfood.member.ratelimit;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.web.AuthCookies;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입 집계 필터의 실행 순서와 동작을 MockMvc로 검증한다 (API_SPEC 1.4, T15).
 * 실제 SecurityConfig 체인을 쓰고, 요청 제한 서비스만 목으로 바꾼다. 요청 제한 DB는 이 테스트와 무관하다.
 * spring-security-test를 쓰지 않으므로, CSRF 토큰은 세션을 통해 실제 CsrfFilter가 발급한 값을 받아 쓴다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        SignupRateLimitFilterTest.ClientIpConfig.class, SignupRateLimitFilterTest.SignupProbe.class})
class SignupRateLimitFilterTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.recommendation.service.RestaurantRecommendationService recommendations;

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

    @MockitoBean
    private AuthApiService authApiService;

    @MockitoBean
    private AuthCookies authCookies;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private com.busanlocalfood.member.jwt.AccessTokenVerifier accessTokenVerifier;

    private MockHttpSession session;
    private String csrf;

    @BeforeEach
    void setUp() throws Exception {
        SignupProbe.REACHED.set(0);
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Allowed());
        MvcResult token = mockMvc.perform(get("/csrf-probe")).andReturn();
        session = (MockHttpSession) token.getRequest().getSession(false);
        csrf = ((CsrfToken) token.getRequest().getAttribute("_csrf")).getToken();
    }

    /** API 체인은 세션이 아니라 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더로 CSRF를 검증한다 (API_SPEC 1.1). */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apiSignup(String body) throws Exception {
        jakarta.servlet.http.Cookie xsrf = mockMvc.perform(get("/api/auth/signup")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(String path, String body) {
        return post(path).session(session).header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void csrf검증_실패는_집계하지_않고_403으로_거부한다() throws Exception {
        mockMvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
                .andExpect(status().isForbidden());

        verify(rateLimitService, never()).recordSignupRequest(any());
        assertThat(SignupProbe.REACHED.get()).isZero();
    }

    @Test
    void csrf검증을_통과한_정상_요청은_집계되고_본문을_컨트롤러가_읽는다() throws Exception {
        mockMvc.perform(signup("/signup", "{\"email\":\"a@b.co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.co"));

        verify(rateLimitService, times(1)).recordSignupRequest("127.0.0.1");
    }

    @Test
    void 잘못된_JSON도_파싱_전에_집계된다() throws Exception {
        mockMvc.perform(signup("/signup", "{not-json"))
                .andExpect(status().isBadRequest());

        verify(rateLimitService, times(1)).recordSignupRequest("127.0.0.1");
    }

    @Test
    void 필수_필드가_없어도_집계된_뒤_검증에서_거부된다() throws Exception {
        mockMvc.perform(signup("/signup", "{}"))
                .andExpect(status().isBadRequest());

        verify(rateLimitService, times(1)).recordSignupRequest("127.0.0.1");
        assertThat(SignupProbe.REACHED.get()).isEqualTo(1);
    }

    @Test
    void 지원하지_않는_Content_Type도_집계된다() throws Exception {
        mockMvc.perform(post("/signup").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.TEXT_PLAIN).content("email=a@b.co"))
                .andExpect(status().isUnsupportedMediaType());

        verify(rateLimitService, times(1)).recordSignupRequest("127.0.0.1");
    }

    @Test
    void API_제한_초과는_JSON_파싱_전에_429로_끝나고_본문을_읽지_않는다() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Limited(42));

        mockMvc.perform(apiSignup("{not-json"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(SignupProbe.REACHED.get()).isZero();
    }

    @Test
    void 화면_제한_초과는_안내_페이지로_전달되고_429와_Retry_After를_유지한다() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Limited(42));

        mockMvc.perform(signup("/signup", "{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(forwardedUrl("/rate-limited"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertThat(SignupProbe.REACHED.get()).isZero();
    }

    @Test
    void 제한_응답_본문에는_IP와_내부_판정_정보가_없다() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Limited(42));

        mockMvc.perform(apiSignup("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("127.0.0.1"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("limit"))));
    }

    @Test
    void 화면과_API_회원가입은_같은_IP_카운터를_공유한다() throws Exception {
        mockMvc.perform(signup("/signup", "{\"email\":\"a@b.co\"}")).andExpect(status().isOk());
        mockMvc.perform(apiSignup("{\"email\":\"c@d.co\"}")).andExpect(status().isOk());

        verify(rateLimitService, times(2)).recordSignupRequest("127.0.0.1");
    }

    @Test
    void 대상이_아닌_POST와_GET은_집계하지_않는다() throws Exception {
        mockMvc.perform(post("/other").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/signup/extra").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/signup")).andExpect(status().isMethodNotAllowed());

        verify(rateLimitService, never()).recordSignupRequest(any());
    }

    @Test
    void 신뢰_프록시가_전달한_X_Forwarded_For_클라이언트를_쓴다() throws Exception {
        mockMvc.perform(signup("/signup", "{\"email\":\"a@b.co\"}").with(request -> {
                    request.setRemoteAddr("10.0.0.1");
                    return request;
                }).header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isOk());

        verify(rateLimitService, times(1)).recordSignupRequest("203.0.113.7");
    }

    @Test
    void 신뢰하지_않는_원격_주소의_X_Forwarded_For는_무시한다() throws Exception {
        mockMvc.perform(signup("/signup", "{\"email\":\"a@b.co\"}").with(request -> {
                    request.setRemoteAddr("198.51.100.9");
                    return request;
                }).header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isOk());

        verify(rateLimitService, times(1)).recordSignupRequest("198.51.100.9");
    }

    @Test
    void 판정을_끝내지_못하면_요청을_진행시키지_않는다_fail_closed() throws Exception {
        when(rateLimitService.recordSignupRequest(any())).thenThrow(new RateLimitUnavailableException());

        mockMvc.perform(signup("/signup", "{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));

        assertThat(SignupProbe.REACHED.get()).isZero();
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }

    @RestController
    static class SignupProbe {

        static final AtomicInteger REACHED = new AtomicInteger();

        /** CSRF 토큰 값을 실제로 읽어야 세션에 토큰이 저장된다 (Spring Security 6의 지연 저장). */
        @GetMapping("/csrf-probe")
        String csrfProbe(CsrfToken token) {
            return token.getToken();
        }

        @PostMapping(value = {"/signup", "/api/auth/signup"})
        ResponseEntity<Object> signup(@RequestBody(required = false) Map<String, String> body) {
            REACHED.incrementAndGet();
            if (body == null || body.get("email") == null) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(new ResponseEntityLike(body.get("email")));
        }
    }

    record ResponseEntityLike(String email) {
    }
}
