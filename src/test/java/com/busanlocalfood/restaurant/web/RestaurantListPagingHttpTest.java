package com.busanlocalfood.restaurant.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 목록 페이지 이동 링크가 검색 조건을 유지하는지 확인한다 (화면 `/restaurants`).
 * 서비스는 목이다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = RestaurantScreenController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import(SecurityConfig.class)
class RestaurantListPagingHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.favorite.service.FavoriteService favoriteService;

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewSummaryService reviewSummaryService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private AccessTokenVerifier accessTokenVerifier;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of());
    }

    @Test
    void 가운데_페이지의_이전_다음_링크는_검색어_카테고리_정렬_크기를_유지한다() throws Exception {
        // 카테고리 3, 검색어 "국밥", 정렬 rating, 크기 5, 2페이지(0부터 세면 page=1), 전체 4페이지
        when(restaurantService.list(eq(3L), eq("국밥"), eq("rating"), eq(1), eq(5)))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 20, 4));

        mockMvc.perform(get("/restaurants").param("category", "3").param("keyword", "국밥")
                        .param("sort", "rating").param("size", "5").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("restaurant-list"))
                .andExpect(content().string(containsString("category=3")))
                .andExpect(content().string(containsString("keyword=%EA%B5%AD%EB%B0%A5")))
                .andExpect(content().string(containsString("sort=rating")))
                .andExpect(content().string(containsString("size=5")))
                .andExpect(content().string(containsString("page=0")))
                .andExpect(content().string(containsString("page=2")));
    }

    @Test
    void 검색_폼에는_page_값이_없어_조건을_바꾸면_첫_페이지부터_조회한다() throws Exception {
        when(restaurantService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/restaurants").param("keyword", "국밥").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"page\""))));
    }
}
