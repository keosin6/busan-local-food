package com.busanlocalfood.favorite.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.favorite.dto.FavoriteDtos.FavoriteState;
import com.busanlocalfood.favorite.service.FavoriteService;
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
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
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
 * 찜 API의 HTTP 동작 (API_SPEC 8.1~8.3). 실제 SecurityConfig와 JWT 검증을 쓴다. 서비스는 목이다.
 * 중복·없음·본인 범위의 실제 결과는 FavoriteDbTest가 본다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = FavoriteApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class FavoriteApiHttpTest {

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
    private FavoriteService favoriteService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private String bearer(long memberId) {
        return "Bearer " + issuer.issue(memberId, Role.USER).token();
    }

    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private static RestaurantSummary summary(long id, String name) {
        return new RestaurantSummary(id, name, new CategoryRef(1L, "한식"), "부산 해운대구",
                new BigDecimal("4.5"), 3, null, LocalDateTime.of(2026, 11, 2, 18, 30));
    }

    @BeforeEach
    void stubs() {
        when(favoriteService.favorite(eq(RESTAURANT_ID), eq(MEMBER_ID)))
                .thenReturn(new FavoriteState(RESTAURANT_ID, true, 4));
        when(favoriteService.unfavorite(eq(RESTAURANT_ID), eq(MEMBER_ID)))
                .thenReturn(new FavoriteState(RESTAURANT_ID, false, 3));
        when(favoriteService.list(eq(MEMBER_ID), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(summary(RESTAURANT_ID, "내 찜 맛집")), 0, 12, 1, 1));
    }

    @Test
    void 비인증_찜하기는_CSRF_토큰이_있어도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/restaurants/10/favorite").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비인증_찜_취소와_찜_목록도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(delete("/api/restaurants/10/favorite").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
    }

    @Test
    void 정상_JWT로_찜하면_201과_찜_상태를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/restaurants/10/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value(10))
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(4));
    }

    @Test
    void 정상_JWT로_찜을_취소하면_200과_찜_상태를_돌려준다() throws Exception {
        mockMvc.perform(delete("/api/restaurants/10/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(3));
    }

    @Test
    void 이미_찜한_맛집을_다시_찜하면_409다() throws Exception {
        when(favoriteService.favorite(eq(RESTAURANT_ID), eq(MEMBER_ID)))
                .thenThrow(new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS));

        mockMvc.perform(post("/api/restaurants/10/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isConflict());
    }

    @Test
    void 찜하지_않은_맛집을_취소하면_404다() throws Exception {
        when(favoriteService.unfavorite(eq(RESTAURANT_ID), eq(MEMBER_ID)))
                .thenThrow(new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));

        mockMvc.perform(delete("/api/restaurants/10/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_맛집에_찜하면_404다() throws Exception {
        when(favoriteService.favorite(eq(999L), eq(MEMBER_ID)))
                .thenThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        mockMvc.perform(post("/api/restaurants/999/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 맛집_ID가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(post("/api/restaurants/abc/favorite").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내_찜_목록은_토큰의_회원_ID로만_조회하고_응답_구조를_지킨다() throws Exception {
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.content[0].name").value("내 찜 맛집"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(favoriteService).list(MEMBER_ID, 0, 12);
    }

    @Test
    void 다른_회원의_토큰은_그_회원의_목록만_조회하게_한다() throws Exception {
        mockMvc.perform(get("/api/me/favorites").header("Authorization", bearer(OTHER_MEMBER_ID)))
                .andExpect(status().isOk());
        verify(favoriteService).list(OTHER_MEMBER_ID, 0, 12);
    }
}
