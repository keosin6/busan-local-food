package com.busanlocalfood.restaurant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.OwnerRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 맛집 이미지 화면 (FUNCTION_SPEC F1-03). 목록 썸네일, 상세 갤러리, 수정 화면의 사진 관리, 화면 제출의 CSRF와 리다이렉트를 확인한다.
 * 서비스는 목이다. 데이터베이스와 S3에 접속하지 않는다.
 */
@WebMvcTest(controllers = {RestaurantScreenController.class, RestaurantImageScreenController.class},
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test-only"
        })
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class RestaurantImageScreenHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.favorite.service.FavoriteService favoriteService;

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    /** JwtConfig가 기동 시 서명 키를 요구한다. 테스트마다 임의로 만든 키를 쓴다. */
    private static final String JWT_SECRET = randomBase64Key();

    @org.springframework.test.context.DynamicPropertySource
    static void jwtSecret(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    private static final String THUMB = "https://img-bucket.s3.ap-northeast-2.amazonaws.com/restaurants/10/a.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewSummaryService reviewSummaryService;

    @MockitoBean
    private RestaurantImageService imageService;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private AccessTokenVerifier accessTokenVerifier;

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        return session;
    }

    private static RestaurantDetail detail(boolean owner, List<ImageSummary> images) {
        return new RestaurantDetail(10L, "이미지 맛집", new CategoryRef(1L, "한식"), "부산 해운대구", null, "설명",
                null, null, new BigDecimal("0.0"), 0, new OwnerRef(7L, "작성자"), images, 0, false, owner,
                null, null);
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of());
        when(reviewSummaryService.summaryFor(anyLong())).thenReturn(new com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse("NOT_ENOUGH_REVIEWS", null, 0, null, null, "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다."));
        when(reviewService.list(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(new com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse<>(List.of(), 0, 10, 0, 0));
    }

    @Test
    void 목록은_대표_이미지_URL을_썸네일로_보여_준다() throws Exception {
        RestaurantSummary withPhoto = new RestaurantSummary(10L, "사진 있음", new CategoryRef(1L, "한식"), "주소",
                new BigDecimal("0.0"), 0, THUMB, null);
        RestaurantSummary without = new RestaurantSummary(11L, "사진 없음 맛집", new CategoryRef(1L, "한식"), "주소",
                new BigDecimal("0.0"), 0, null, null);
        when(restaurantService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(withPhoto, without), 0, 12, 2, 1));

        mockMvc.perform(get("/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + THUMB + "\"")))
                .andExpect(content().string(containsString("사진 없음")));
    }

    @Test
    void 상세는_이미지_갤러리를_보여_준다() throws Exception {
        when(restaurantService.detail(eq(10L), any())).thenReturn(detail(false,
                List.of(new ImageSummary(100L, THUMB, 0))));

        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + THUMB + "\"")))
                .andExpect(content().string(not(containsString("등록된 사진이 없습니다"))));
    }

    @Test
    void 상세에_이미지가_없으면_기본_문구를_보여_준다() throws Exception {
        when(restaurantService.detail(eq(10L), any())).thenReturn(detail(false, List.of()));

        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 사진이 없습니다")));
    }

    @Test
    void 수정_화면은_사진_목록과_업로드_폼과_삭제_버튼을_보여_준다() throws Exception {
        when(restaurantService.loadForEdit(10L, 7L)).thenReturn(detail(true,
                List.of(new ImageSummary(100L, THUMB, 0))));

        mockMvc.perform(get("/restaurants/10/edit").session(loggedInSession(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/restaurants/10/images\"")))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("/restaurants/10/images/100/delete")));
    }

    @Test
    void 수정_화면은_업로드_결과_안내_문구를_보여_준다() throws Exception {
        when(restaurantService.loadForEdit(10L, 7L)).thenReturn(detail(true, List.of()));

        mockMvc.perform(get("/restaurants/10/edit").param("image", "added").session(loggedInSession(7L)))
                .andExpect(content().string(containsString("사진을 추가했습니다.")));
        mockMvc.perform(get("/restaurants/10/edit").param("imageError", "type").session(loggedInSession(7L)))
                .andExpect(content().string(containsString("JPEG, PNG, WEBP 형식만 올릴 수 있습니다.")));
        mockMvc.perform(get("/restaurants/10/edit").param("imageError", "limit").session(loggedInSession(7L)))
                .andExpect(content().string(containsString("최대 5장")));
    }

    @Test
    void 사진_삭제는_수정_화면으로_돌아가고_S3_지연이면_안내를_붙인다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        when(restaurantService.loadForEdit(10L, 7L)).thenReturn(detail(true, List.of(new ImageSummary(100L, THUMB, 0))));
        String token = csrfFromEditPage(session);
        when(imageService.delete(10L, 100L, 7L)).thenReturn(new ImageDeleteResult(100L, false));

        mockMvc.perform(post("/restaurants/10/images/100/delete").session(session).param("_csrf", token))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/restaurants/10/edit?image=deleted&storage=1"));
    }

    @Test
    void 사진_업로드는_로그인하고_CSRF_토큰이_있어야_하며_없으면_403이다() throws Exception {
        MockHttpSession session = loggedInSession(7L);

        mockMvc.perform(post("/restaurants/10/images").session(session))
                .andExpect(status().isForbidden());
    }

    private String csrfFromEditPage(MockHttpSession session) throws Exception {
        MvcResult page = mockMvc.perform(get("/restaurants/10/edit").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("수정 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }
}
