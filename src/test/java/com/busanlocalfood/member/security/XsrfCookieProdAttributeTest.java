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

/** 운영 프로필에서는 XSRF-TOKEN 쿠키에 Secure가 붙는다 (API_SPEC 1.1). */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@ActiveProfiles("prod")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        ApiSecurityFilterChainTest.ClientIpConfig.class, ApiSecurityFilterChainTest.Probes.class})
class XsrfCookieProdAttributeTest {

    @MockitoBean
    private RestaurantService restaurantService;

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

    @Test
    void 운영에서_XSRF_쿠키는_Secure이고_HttpOnly가_아니며_SameSite_Lax_Path_루트다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me")).andReturn();
        String cookie = XsrfCookieAttributeTest.xsrfSetCookie(result);

        assertThat(cookie).contains("Secure").contains("Path=/");
        assertThat(cookie).doesNotContain("HttpOnly");
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Lax");
    }
}
