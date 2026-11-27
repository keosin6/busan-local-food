package com.busanlocalfood.restaurant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.OwnerRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.service.RestaurantService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 맛집 API의 HTTP 동작 (API_SPEC 3.1, 4.1~4.5). 실제 SecurityConfig와 실제 JWT 검증을 쓴다.
 * 서비스는 목이다. 작성자 확인과 검증 규칙은 서비스 단위 테스트가 본다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = RestaurantApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class RestaurantApiHttpTest {

    private static final String JWT_SECRET = randomBase64Key();
    private static final long MEMBER_ID = 7L;
    private static final long OTHER_MEMBER_ID = 8L;

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
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private static final String BODY = """
            {"name":"해운대 국밥","categoryId":1,"address":"부산 해운대구","phone":"051-000-0000",
             "description":"국밥 맛집","latitude":35.1587,"longitude":129.1604}
            """;

    private String bearer(long memberId) {
        return "Bearer " + issuer.issue(memberId, Role.USER).token();
    }

    private static RestaurantDetail detail(long memberId, boolean owner) {
        return new RestaurantDetail(10L, "해운대 국밥", new CategoryRef(1L, "한식"), "부산 해운대구", "051-000-0000",
                "국밥 맛집", null, null, new BigDecimal("0.0"), 0, new OwnerRef(memberId, "작성자"),
                List.of(), 0, false, owner, null, null);
    }

    /** API 체인의 CSRF 쿠키를 받는다. 쓰기 요청에 헤더와 함께 보낸다. */
    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of(new CategoryResponse(1L, "한식", 1)));
        when(restaurantService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    @Test
    void 카테고리는_로그인_없이_조회한다() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("한식"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1));
    }

    @Test
    void 맛집_목록은_로그인_없이_조회하고_기본_정렬과_페이지를_쓴다() throws Exception {
        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12));
        verify(restaurantService).list(null, null, "latest", 0, 12);
    }

    @Test
    void 맛집_목록은_카테고리_검색어_정렬을_서비스에_넘긴다() throws Exception {
        mockMvc.perform(get("/api/restaurants").param("category", "3").param("keyword", "국밥")
                        .param("sort", "rating").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        verify(restaurantService).list(3L, "국밥", "rating", 2, 5);
    }

    @Test
    void 맛집_상세는_비회원도_조회하고_isOwner는_false다() throws Exception {
        when(restaurantService.detail(10L, null)).thenReturn(detail(MEMBER_ID, false));

        mockMvc.perform(get("/api/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(false))
                .andExpect(jsonPath("$.data.name").value("해운대 국밥"));
    }

    @Test
    void 맛집_상세는_로그인한_작성자에게_isOwner가_true다() throws Exception {
        when(restaurantService.detail(10L, MEMBER_ID)).thenReturn(detail(MEMBER_ID, true));

        mockMvc.perform(get("/api/restaurants/10").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(true));
    }

    @Test
    void 등록은_JWT_없이_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/restaurants").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록은_JWT가_있으면_CSRF_없이도_201이고_작성자로_등록한다() throws Exception {
        when(restaurantService.create(eq(MEMBER_ID), any())).thenReturn(detail(MEMBER_ID, true));

        mockMvc.perform(post("/api/restaurants").header("Authorization", bearer(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isOwner").value(true));
    }

    @Test
    void 등록에서_카테고리가_없으면_400과_CATEGORY_NOT_FOUND다() throws Exception {
        when(restaurantService.create(eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(post("/api/restaurants").header("Authorization", bearer(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void 등록에서_입력이_잘못되면_400과_VALIDATION_FAILED다() throws Exception {
        when(restaurantService.create(eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

        mockMvc.perform(post("/api/restaurants").header("Authorization", bearer(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 수정은_작성자가_아니면_403과_FORBIDDEN이다() throws Exception {
        when(restaurantService.update(eq(10L), eq(OTHER_MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/restaurants/10").header("Authorization", bearer(OTHER_MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 수정은_없는_맛집이면_404와_RESTAURANT_NOT_FOUND다() throws Exception {
        when(restaurantService.update(eq(99L), eq(MEMBER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        mockMvc.perform(put("/api/restaurants/99").header("Authorization", bearer(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESTAURANT_NOT_FOUND"));
    }

    @Test
    void 수정은_작성자면_200이다() throws Exception {
        when(restaurantService.update(eq(10L), eq(MEMBER_ID), any())).thenReturn(detail(MEMBER_ID, true));

        mockMvc.perform(put("/api/restaurants/10").header("Authorization", bearer(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(true));
    }

    @Test
    void 삭제는_작성자면_200과_이미지_수_0을_돌려준다() throws Exception {
        when(restaurantService.delete(10L, MEMBER_ID)).thenReturn(new DeleteResult(10L, 0, 0));

        mockMvc.perform(delete("/api/restaurants/10").header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantId").value(10))
                .andExpect(jsonPath("$.data.deletedImageCount").value(0))
                .andExpect(jsonPath("$.data.storageDeleteFailedCount").value(0));
    }

    @Test
    void 삭제는_작성자가_아니면_403이다() throws Exception {
        when(restaurantService.delete(10L, OTHER_MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/restaurants/10").header("Authorization", bearer(OTHER_MEMBER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 삭제는_JWT_없이_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(delete("/api/restaurants/10").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 수정과_삭제는_유효한_CSRF가_있어도_JWT_없으면_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(put("/api/restaurants/10").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/restaurants/10").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 쿠키_세션_요청의_수정과_삭제는_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(put("/api/restaurants/10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/restaurants/10")).andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_토큰으로_쓰기_요청하면_401이다() throws Exception {
        mockMvc.perform(delete("/api/restaurants/10").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_토큰이어도_공개_상세_조회는_비회원으로_처리한다() throws Exception {
        mockMvc.perform(get("/api/restaurants/10").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
        verify(restaurantService).detail(10L, null);
    }
}
