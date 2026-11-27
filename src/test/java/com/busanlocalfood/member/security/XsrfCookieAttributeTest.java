package com.busanlocalfood.member.security;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.web.AuthCookies;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** XSRF-TOKEN 쿠키 속성이 API_SPEC 1.1과 일치하는지 실제 응답으로 확인한다 (로컬 프로필). */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        ApiSecurityFilterChainTest.ClientIpConfig.class, ApiSecurityFilterChainTest.Probes.class})
class XsrfCookieAttributeTest {

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
    private GoogleSignupService googleSignupService;

    @MockitoBean
    private AuthApiService authApiService;

    @MockitoBean
    private AuthCookies authCookies;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    static String xsrfSetCookie(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
    }

    @Test
    void 로컬에서_XSRF_쿠키는_HttpOnly가_아니고_SameSite_Lax이며_Path가_루트다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me")).andReturn();
        String cookie = xsrfSetCookie(result);

        assertThat(cookie).contains("Path=/");
        assertThat(cookie).doesNotContain("HttpOnly");
        assertThat(cookie).doesNotContain("Secure");
        // SameSite는 서블릿 6의 쿠키 속성으로 붙는다. MockHttpServletResponse는 이 속성을 헤더 문자열에 쓰지 않으므로 쿠키 객체에서 확인한다.
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void 화면_응답에는_XSRF_쿠키를_내려주지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/csrf-none-screen")).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie").stream()
                .noneMatch(value -> value.startsWith("XSRF-TOKEN="))).isTrue();
    }
}
