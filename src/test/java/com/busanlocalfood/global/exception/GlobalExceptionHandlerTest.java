package com.busanlocalfood.global.exception;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.web.AuthCookies;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitUnavailableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, GlobalExceptionHandlerTest.FailingController.class})
class GlobalExceptionHandlerTest {

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private com.busanlocalfood.member.jwt.AccessTokenVerifier accessTokenVerifier;

    @Test
    void businessExceptionIsRenderedAsApiErrorResponse() throws Exception {
        mockMvc.perform(get("/test-errors/business"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rateLimitExceededIsRenderedAs429WithRetryAfterAndNoInternals() throws Exception {
        mockMvc.perform(get("/test-errors/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(not(containsString("42"))));
    }

    @Test
    void rateLimitUnavailableFailsClosedAsInternalErrorWithoutRetryAfter() throws Exception {
        mockMvc.perform(get("/test-errors/rate-limit-unavailable"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test-errors/business")
        void fail() {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        @GetMapping("/test-errors/rate-limited")
        void rateLimited() {
            throw new RateLimitExceededException(42);
        }

        @GetMapping("/test-errors/rate-limit-unavailable")
        void unavailable() {
            throw new RateLimitUnavailableException();
        }
    }
}
