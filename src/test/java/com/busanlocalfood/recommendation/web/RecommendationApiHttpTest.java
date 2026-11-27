package com.busanlocalfood.recommendation.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.Recommendation;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RestaurantRecommendationService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
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
 * AI 추천 API의 HTTP 동작 (API_SPEC 9.1). 실제 SecurityConfig와 JWT 검증을 쓴다. 서비스는 목이다.
 * 취향 검증과 AI 처리의 실제 결과는 서비스 테스트가 본다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = RecommendationApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class RecommendationApiHttpTest {

    private static final String JWT_SECRET = randomBase64Key();
    private static final long USER_ID = 7L;
    private static final long ADMIN_ID = 9L;
    private static final String BODY = "{\"preference\":\"2명이서 조용히 먹을 매운 국물 요리\"}";

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

    @Autowired
    private AccessTokenIssuer issuer;

    @MockitoBean
    private RestaurantRecommendationService recommendations;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private String bearer(long memberId, Role role) {
        return "Bearer " + issuer.issue(memberId, role).token();
    }

    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private static RecommendationResponse one() {
        return new RecommendationResponse(List.of(new Recommendation(10L, "해운대 돼지국밥", "한식",
                new BigDecimal("4.5"), "진한 국물이 취향에 맞습니다.")), 42, null);
    }

    @BeforeEach
    void stubs() {
        when(recommendations.recommend(eq("2명이서 조용히 먹을 매운 국물 요리"))).thenReturn(one());
    }

    @Test
    void 비인증_요청은_CSRF_토큰이_있어도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/recommendations").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void USER_토큰의_정상_요청은_추천_구조로_응답한다() throws Exception {
        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendations[0].restaurantId").value(10))
                .andExpect(jsonPath("$.data.recommendations[0].name").value("해운대 돼지국밥"))
                .andExpect(jsonPath("$.data.recommendations[0].category").value("한식"))
                .andExpect(jsonPath("$.data.recommendations[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.data.recommendations[0].reason").value("진한 국물이 취향에 맞습니다."))
                .andExpect(jsonPath("$.data.candidateCount").value(42))
                .andExpect(jsonPath("$.data.message").doesNotExist());
    }

    @Test
    void ADMIN_토큰도_같은_API를_쓸_수_있다() throws Exception {
        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(ADMIN_ID, Role.ADMIN))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateCount").value(42));
    }

    @Test
    void 취향이_없거나_범위를_벗어나면_400이다() throws Exception {
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content("{\"preference\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 알_수_없는_필드가_있으면_400이다() throws Exception {
        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content("{\"preference\":\"국밥\",\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void AI_호출_실패는_502_AI_UNAVAILABLE이다() throws Exception {
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void AI_응답_검증_실패는_502_AI_RESPONSE_INVALID이다() throws Exception {
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.AI_RESPONSE_INVALID));

        mockMvc.perform(post("/api/recommendations").header("Authorization", bearer(USER_ID, Role.USER))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }
}
