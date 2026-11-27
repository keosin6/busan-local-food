package com.busanlocalfood.restaurant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 맛집 이미지 API의 HTTP 동작 (API_SPEC 5.1, 5.2). 실제 SecurityConfig와 JWT 검증을 쓴다.
 * 서비스는 목이다. 작성자 확인과 저장 흐름은 서비스 테스트와 DB 테스트가 본다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = RestaurantImageApiController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class RestaurantImageApiHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.favorite.service.FavoriteService favoriteService;

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
    private RestaurantImageService imageService;

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

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    private String bearer(long memberId) {
        return "Bearer " + issuer.issue(memberId, Role.USER).token();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "food.jpg", "image/jpeg", JPEG);
    }

    /** API 체인의 CSRF 쿠키를 받는다. 쿠키 인증 요청에만 필요하다. */
    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @BeforeEach
    void stubs() {
        when(imageService.upload(anyLong(), anyLong(), any())).thenReturn(new ImageSummary(100L,
                "https://bucket.s3.ap-northeast-2.amazonaws.com/restaurants/10/x.jpg", 0));
    }

    @Test
    void 작성자는_JWT로_이미지를_올리면_201과_이미지_정보를_받는다() throws Exception {
        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.url").exists());
        verify_upload_called_with_member(MEMBER_ID);
    }

    private void verify_upload_called_with_member(long memberId) {
        org.mockito.Mockito.verify(imageService).upload(eq(RESTAURANT_ID), eq(memberId), any());
    }

    @Test
    void 로그인_없이_올리면_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 허용되지_않는_형식은_400과_IMAGE_TYPE_NOT_ALLOWED다() throws Exception {
        when(imageService.upload(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED));

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void 개수_초과는_400과_IMAGE_LIMIT_EXCEEDED다() throws Exception {
        when(imageService.upload(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED));

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_LIMIT_EXCEEDED"));
    }

    @Test
    void S3_업로드_실패는_502와_STORAGE_ERROR다() throws Exception {
        when(imageService.upload(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.STORAGE_ERROR));

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("STORAGE_ERROR"));
    }

    @Test
    void 요청_크기_제한을_넘으면_API는_400과_IMAGE_TOO_LARGE_JSON을_돌려준다() throws Exception {
        when(imageService.upload(anyLong(), anyLong(), any()))
                .thenThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID).file(jpeg())
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    void 파일이_없으면_서비스가_VALIDATION_FAILED를_내고_400이다() throws Exception {
        when(imageService.upload(anyLong(), anyLong(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

        mockMvc.perform(multipart("/api/restaurants/{id}/images", RESTAURANT_ID)
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 작성자는_이미지를_지우면_200과_storageDeleted를_받는다() throws Exception {
        when(imageService.delete(RESTAURANT_ID, 100L, MEMBER_ID)).thenReturn(new ImageDeleteResult(100L, true));

        mockMvc.perform(delete("/api/restaurants/{id}/images/{imageId}", RESTAURANT_ID, 100L)
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageId").value(100))
                .andExpect(jsonPath("$.data.storageDeleted").value(true));
    }

    @Test
    void S3_삭제가_실패해도_DB_삭제_응답은_200이고_storageDeleted는_false다() throws Exception {
        when(imageService.delete(RESTAURANT_ID, 100L, MEMBER_ID)).thenReturn(new ImageDeleteResult(100L, false));

        mockMvc.perform(delete("/api/restaurants/{id}/images/{imageId}", RESTAURANT_ID, 100L)
                        .header("Authorization", bearer(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storageDeleted").value(false));
    }

    @Test
    void 작성자가_아니면_이미지_삭제는_403이다() throws Exception {
        when(imageService.delete(RESTAURANT_ID, 100L, OTHER_MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/restaurants/{id}/images/{imageId}", RESTAURANT_ID, 100L)
                        .header("Authorization", bearer(OTHER_MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 이미지_삭제에_JWT가_없으면_유효한_CSRF가_있어도_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(delete("/api/restaurants/{id}/images/{imageId}", RESTAURANT_ID, 100L)
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }
}
