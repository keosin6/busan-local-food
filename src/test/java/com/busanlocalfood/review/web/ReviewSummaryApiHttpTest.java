package com.busanlocalfood.review.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse;
import com.busanlocalfood.review.service.ReviewSummaryService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI 리뷰 요약 API의 HTTP 동작 (API_SPEC 7.1). 공개 조회이므로 로그인 없이 200이어야 한다.
 * 서비스는 목이다. 외부 AI 호출은 ReviewSummaryDbTest가 목으로 확인한다.
 */
@WebMvcTest(controllers = ReviewSummaryApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class ReviewSummaryApiHttpTest {

    private static final String JWT_SECRET = randomBase64Key();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewSummaryService summaryService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void stubs() {
        when(summaryService.summaryFor(10L)).thenReturn(new SummaryResponse("READY", "맛이 진합니다.", 12, true,
                LocalDateTime.of(2026, 11, 3, 9, 0), null));
        when(summaryService.summaryFor(2L)).thenReturn(new SummaryResponse("NOT_ENOUGH_REVIEWS", null, 2, null, null,
                "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다."));
        when(summaryService.summaryFor(3L)).thenReturn(new SummaryResponse("UNAVAILABLE", null, 12, null, null,
                "요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."));
        when(summaryService.summaryFor(99L)).thenThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    void 요약은_로그인_없이_READY와_캐시_정보를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/restaurants/10/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.summary").value("맛이 진합니다."))
                .andExpect(jsonPath("$.data.reviewCount").value(12))
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 리뷰가_부족하면_200과_NOT_ENOUGH_REVIEWS와_안내_문구를_돌려준다() throws Exception {
        mockMvc.perform(get("/api/restaurants/2/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_ENOUGH_REVIEWS"))
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.message").value("리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다."));
    }

    @Test
    void AI_실패는_오류가_아니라_200과_UNAVAILABLE이다() throws Exception {
        mockMvc.perform(get("/api/restaurants/3/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.summary").doesNotExist());
    }

    @Test
    void 없는_맛집은_404_RESTAURANT_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/api/restaurants/99/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESTAURANT_NOT_FOUND"));
    }
}
