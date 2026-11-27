package com.busanlocalfood.review.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.OwnerRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.review.dto.ReviewDtos.AuthorRef;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewItem;
import com.busanlocalfood.review.service.ReviewService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 맛집 상세 화면의 리뷰 표시와 리뷰 제출 (FUNCTION_SPEC F1-04). 화면 세션과 CSRF를 쓴다. 서비스는 목이다.
 * 확인: 비로그인은 작성 폼 대신 로그인 안내, 본인 리뷰에만 삭제 버튼, 제출의 CSRF와 안내 문구 분기.
 */
@WebMvcTest(controllers = {RestaurantScreenController.class, ReviewScreenController.class},
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test-only"
        })
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class ReviewScreenHttpTest {

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
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
    private RestaurantService restaurantService;

    @MockitoBean
    private ReviewService reviewService;

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

    private static MockHttpSession loggedInSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        return session;
    }

    private static RestaurantDetail detail() {
        return new RestaurantDetail(10L, "리뷰 맛집", new CategoryRef(1L, "한식"), "부산 해운대구", null, "설명",
                null, null, new BigDecimal("4.5"), 2, new OwnerRef(1L, "작성자"), List.of(), 0, false, false,
                null, null);
    }

    private static ReviewItem review(long id, long authorId, String nickname, boolean mine) {
        return new ReviewItem(id, 4, "맛있어요 " + nickname, new AuthorRef(authorId, nickname), mine,
                LocalDateTime.of(2026, 11, 2, 18, 30), LocalDateTime.of(2026, 11, 2, 18, 30));
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of());
        when(reviewSummaryService.summaryFor(anyLong())).thenReturn(new com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse("NOT_ENOUGH_REVIEWS", null, 0, null, null, "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다."));
        when(restaurantService.detail(eq(10L), any())).thenReturn(detail());
        when(reviewService.list(anyLong(), any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(
                List.of(review(1L, 7L, "나의리뷰", true), review(2L, 8L, "남의리뷰", false)), 0, 10, 2, 1));
    }

    private String csrfFromDetail(MockHttpSession session) throws Exception {
        MvcResult page = mockMvc.perform(get("/restaurants/10").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("상세 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @Test
    void 상세_화면은_요약이_있으면_요약_본문을_보여_준다() throws Exception {
        when(reviewSummaryService.summaryFor(10L)).thenReturn(new com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse(
                "READY", "진한 국물이 특징입니다.", 4, true, LocalDateTime.of(2026, 11, 3, 9, 0), null));

        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 리뷰 요약")))
                .andExpect(content().string(containsString("진한 국물이 특징입니다.")));
    }

    @Test
    void 상세_화면은_요약이_실패해도_나머지_정보와_함께_정상_표시한다() throws Exception {
        when(reviewSummaryService.summaryFor(10L)).thenReturn(new com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse(
                "UNAVAILABLE", null, 4, null, null, "요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."));

        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.")))
                .andExpect(content().string(containsString("남의리뷰")));
    }

    @Test
    void 비로그인_상세는_목록을_보여_주고_작성_폼_대신_로그인_안내를_보인다() throws Exception {
        // 익명 조회자에게는 어떤 리뷰도 본인 것이 아니다. 목은 조회자 ID가 null일 때 본인 여부를 false로 준다.
        when(reviewService.list(eq(10L), any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(
                List.of(review(1L, 7L, "나의리뷰", false), review(2L, 8L, "남의리뷰", false)), 0, 10, 2, 1));
        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("남의리뷰")))
                .andExpect(content().string(containsString("리뷰를 작성하려면")))
                .andExpect(content().string(not(containsString("action=\"/restaurants/10/reviews\""))))
                .andExpect(content().string(not(containsString("/reviews/1/delete"))))
                .andExpect(content().string(not(containsString("/reviews/2/delete"))));
    }

    @Test
    void 로그인_상세는_작성_폼과_본인_리뷰의_삭제_버튼만_보여_준다() throws Exception {
        mockMvc.perform(get("/restaurants/10").session(loggedInSession(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/restaurants/10/reviews\"")))
                .andExpect(content().string(containsString("/reviews/1/delete")))
                .andExpect(content().string(not(containsString("/reviews/2/delete"))));
    }

    @Test
    void 리뷰_작성은_로그인과_CSRF가_있어야_하고_없으면_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/10/reviews").session(loggedInSession(7L))
                        .param("rating", "5").param("content", "좋아요"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 리뷰_작성_성공은_상세_화면의_리뷰_영역으로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrfFromDetail(session);
        when(reviewService.create(eq(10L), eq(7L), any())).thenReturn(
                new ReviewCreated(3L, 5, "좋아요", new BigDecimal("4.7"), 3));

        mockMvc.perform(post("/restaurants/10/reviews").session(session).param("_csrf", token)
                        .param("rating", "5").param("content", "좋아요"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/restaurants/10?review=added#reviews"));
    }

    @Test
    void 리뷰_작성의_중복과_입력_오류는_각각_안내_코드로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrfFromDetail(session);
        when(reviewService.create(eq(10L), eq(7L), any()))
                .thenThrow(new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS));

        mockMvc.perform(post("/restaurants/10/reviews").session(session).param("_csrf", token)
                        .param("rating", "4").param("content", "중복"))
                .andExpect(redirectedUrl("/restaurants/10?reviewError=duplicate#reviews"));

        when(reviewService.create(eq(10L), eq(7L), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));
        mockMvc.perform(post("/restaurants/10/reviews").session(session).param("_csrf", token)
                        .param("rating", "abc").param("content", "x"))
                .andExpect(redirectedUrl("/restaurants/10?reviewError=invalid#reviews"));
    }

    @Test
    void 본인_리뷰_삭제는_상세로_돌아가_삭제_안내를_보인다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrfFromDetail(session);

        mockMvc.perform(post("/reviews/1/delete").session(session).param("_csrf", token).param("restaurantId", "10"))
                .andExpect(redirectedUrl("/restaurants/10?review=deleted#reviews"));
    }

    @Test
    void 타인_리뷰_삭제는_403이_아니라_안내_코드로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(7L);
        String token = csrfFromDetail(session);
        when(reviewService.delete(eq(2L), eq(7L)))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/reviews/2/delete").session(session).param("_csrf", token).param("restaurantId", "10"))
                .andExpect(redirectedUrl("/restaurants/10?reviewError=forbidden#reviews"));
    }

    @Test
    void 비로그인_리뷰_작성은_CSRF_토큰이_없으면_403이다() throws Exception {
        // 로그인 화면 리다이렉트는 SecurityConfig의 /restaurants/*/reviews 인증 규칙이 맡는다.
        // 이 슬라이스에는 로그인 컨트롤러가 없어 토큰을 얻을 수 없으므로, 여기서는 토큰 없는 제출이 막히는 것만 본다.
        mockMvc.perform(post("/restaurants/10/reviews").session(new MockHttpSession())
                        .param("rating", "5").param("content", "익명"))
                .andExpect(status().isForbidden());
    }
}
