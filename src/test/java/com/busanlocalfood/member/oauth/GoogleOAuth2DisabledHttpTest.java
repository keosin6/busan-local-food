package com.busanlocalfood.member.oauth;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Google 설정(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)이 없는 local 기동: 앱은 뜨고 Google 경로는 노출되지 않는다.
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
        GoogleOAuth2DisabledHttpTest.ClientIpConfig.class})
class GoogleOAuth2DisabledHttpTest {

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

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
    private GoogleLoginService googleLoginService;

    @MockitoBean
    private GoogleSignupService googleSignupService;

    @MockitoBean
    private AuthApiService authService;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void Google_설정이_없으면_시작_경로가_노출되지_않는다() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void Google_설정이_없으면_로그인_화면에_Google_버튼이_없다() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("/oauth2/authorization/google");
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
