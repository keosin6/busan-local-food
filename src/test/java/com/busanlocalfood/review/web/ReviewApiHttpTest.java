package com.busanlocalfood.review.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.review.dto.ReviewDtos.AuthorRef;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewDeleteResult;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewItem;
import com.busanlocalfood.review.service.ReviewService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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
 * 리뷰 API의 HTTP 동작 (API_SPEC 6.1~6.3). 실제 SecurityConfig와 JWT 검증을 쓴다. 서비스는 목이다.
 * 중복·권한·존재 확인의 실제 결과는 DB 테스트가 본다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = ReviewApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class ReviewApiHttpTest {

    private static final String JWT_SECRET = randomBase64Key();
    private static final long MEMBER_ID = 7L;
    private static final long OTHER_MEMBER_ID = 8L;
    private static final long RESTAURANT_ID = 10L;

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
    private ReviewService reviewService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private static final String BODY = "{\"rating\":5,\"content\":\"국물이 진해요\"}";

    private String bearer(long memberId) {
        return "Bearer " + issuer.issue(memberId, Role.USER).token();
    }

    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @BeforeEach
    void stubs() {
        when(reviewService.list(anyLong(), any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(
                List.of(new ReviewItem(200L, 5, "국물이 진해요", new AuthorRef(MEMBER_ID, "맛객"), true,
                        LocalDateTime.of(2026, 11, 2, 18, 30), LocalDateTime.of(2026, 11, 2, 18, 30))),
                0, 10, 1, 1));
    }

    @Test
    void 리뷰_목록은_로그인_없이_조회하고_본인_여부를_준다() throws Exception {
        mockMvc.perform(get("/api/restaurants/10/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].author.nickname").value("맛객"))
                .andExpect(jsonPath("$.data.content[0].isMine").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 리뷰_작성은_JWT가_있으면_201과_갱신된_맛집_값을_돌려준다() throws Exception {
        when(reviewService.create(eq(RESTAURANT_ID), eq(MEMBER_ID), any())).thenReturn(new ReviewCreated(200L, 5,
                "국물이 진해요", new BigDecimal("4.5"), 2));

        mockMvc.perform(post("/api/restaurants/10/reviews").header("Authorization", bearer(MEMBER_ID))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.restaurantAverageRating").value(4.5))
                .andExpect(jsonPath("$.data.restaurantReviewCount").value(2));
    }

    @Test
    void 로그인_없이_리뷰를_쓰면_CSRF_토큰이_있어도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/restaurants/10/reviews").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 평점_범위_오류는_400_VALIDATION_FAILED다() throws Exception {
        when(reviewService.create(eq(RESTAURANT_ID), eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

        mockMvc.perform(post("/api/restaurants/10/reviews").header("Authorization", bearer(MEMBER_ID))
                        .contentType("application/json").content("{\"rating\":6,\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 같은_맛집에_두_번_쓰면_409_REVIEW_ALREADY_EXISTS다() throws Exception {
        when(reviewService.create(eq(RESTAURANT_ID), eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS));

        mockMvc.perform(post("/api/restaurants/10/reviews").header("Authorization", bearer(MEMBER_ID))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void 없는_맛집에_쓰면_404_RESTAURANT_NOT_FOUND다() throws Exception {
        when(reviewService.create(eq(99L), eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        mockMvc.perform(post("/api/restaurants/99/reviews").header("Authorization", bearer(MEMBER_ID))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESTAURANT_NOT_FOUND"));
    }

    @Test
    void 본인_리뷰_삭제는_200과_갱신된_맛집_값을_돌려준다() throws Exception {
        when(reviewService.delete(200L, MEMBER_ID)).thenReturn(new com.busanlocalfood.review.service.ReviewService.DeletedReview(
                RESTAURANT_ID, new ReviewDeleteResult(new BigDecimal("4.5"), 1)));

        mockMvc.perform(delete("/api/reviews/200").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantAverageRating").value(4.5))
                .andExpect(jsonPath("$.data.restaurantReviewCount").value(1));
    }

    @Test
    void 타인의_리뷰_삭제는_403_FORBIDDEN이다() throws Exception {
        when(reviewService.delete(200L, OTHER_MEMBER_ID)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/reviews/200").header("Authorization", bearer(OTHER_MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_리뷰_삭제는_404_REVIEW_NOT_FOUND다() throws Exception {
        when(reviewService.delete(404L, MEMBER_ID)).thenThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        mockMvc.perform(delete("/api/reviews/404").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void 리뷰_삭제에_JWT가_없으면_유효한_CSRF가_있어도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(delete("/api/reviews/200").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }
}
