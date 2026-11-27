package com.busanlocalfood.member.web;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.LoginAttemptResult;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.MemberLoginService;
import com.busanlocalfood.member.service.MemberRegistrationService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 운영 프로필에서 API 인증 쿠키에 Secure가 붙는지 확인한다 (API_SPEC 1.1). */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@ActiveProfiles("prod")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthApiService.class, AuthCookies.class, AuthController.class,
        AuthApiProdCookieTest.ClientIpConfig.class})
class AuthApiProdCookieTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminQueryService adminQueries;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminStorageRetryService adminStorageRetry;

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

    private static final String JWT_SECRET = randomBase64Key();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void 운영_프로필에서는_로그인_쿠키에_Secure가_붙는다() throws Exception {
        Member member = Member.create("prod@example.com", "$2a$hash", "운영회원", null);
        ReflectionTestUtils.setField(member, "id", 9L);
        when(rateLimit.attemptLogin(anyString(), anyString(), any())).thenAnswer(invocation -> {
            BooleanSupplier credentials = invocation.getArgument(2);
            return credentials.getAsBoolean()
                    ? new LoginAttemptResult.Authenticated()
                    : new LoginAttemptResult.InvalidCredentials();
        });
        when(loginService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.of(member));
        when(refreshTokens.issueForNewLogin(9L)).thenReturn(
                new IssuedRefreshToken("prod-refresh-raw", LocalDateTime.now().plusDays(14)));

        var xsrf = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prod@example.com\",\"password\":\"Passw0rd!\"}")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).allMatch(h -> h.contains("Secure") && h.contains("HttpOnly") && h.contains("SameSite=Lax"));
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
