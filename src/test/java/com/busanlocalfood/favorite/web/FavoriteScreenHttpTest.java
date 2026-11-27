package com.busanlocalfood.favorite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.favorite.dto.FavoriteDtos.FavoriteState;
import com.busanlocalfood.favorite.service.FavoriteService;
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
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.review.service.ReviewService;
import com.busanlocalfood.review.service.ReviewSummaryService;
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
 * 찜 화면 (FUNCTION_SPEC F1-06). 화면 세션과 CSRF를 쓴다. 서비스는 목이다.
 * 확인: 비회원 차단, 상세의 찜 버튼 상태, 찜하기와 취소의 리다이렉트와 안내, CSRF 검증, 찜 목록 화면과 빈 목록.
 */
@WebMvcTest(controllers = {RestaurantScreenController.class, FavoriteScreenController.class},
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test-only"
        })
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class FavoriteScreenHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.recommendation.service.RestaurantRecommendationService recommendations;

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String JWT_SECRET = randomBase64Key();
    private static final long MEMBER_ID = 7L;
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

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReviewSummaryService reviewSummaryService;

    @MockitoBean
    private FavoriteService favoriteService;

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

    private static RestaurantDetail detail(boolean favorited, int favoriteCount) {
        return new RestaurantDetail(RESTAURANT_ID, "찜 맛집", new CategoryRef(1L, "한식"), "부산 해운대구", null, "설명",
                null, null, new BigDecimal("4.5"), 2, new OwnerRef(1L, "작성자"), List.of(), favoriteCount,
                favorited, false, null, null);
    }

    private static RestaurantSummary summary(long id, String name) {
        return new RestaurantSummary(id, name, new CategoryRef(1L, "한식"), "부산 해운대구",
                new BigDecimal("4.5"), 2, null, LocalDateTime.of(2026, 11, 2, 18, 30));
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of());
        when(restaurantService.detail(eq(RESTAURANT_ID), any())).thenReturn(detail(false, 0));
        when(reviewService.list(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));
        when(reviewSummaryService.summaryFor(anyLong())).thenReturn(
                new com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse("NOT_ENOUGH_REVIEWS", null, 0, null, null,
                        "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다."));
        when(favoriteService.list(eq(MEMBER_ID), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(summary(RESTAURANT_ID, "찜 맛집")), 0, 12, 1, 1));
    }

    private String csrfFromDetail(MockHttpSession session) throws Exception {
        MvcResult page = mockMvc.perform(get("/restaurants/10").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("상세 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @Test
    void 비회원이_찜_목록에_들어가면_로그인으로_보낸다() throws Exception {
        mockMvc.perform(get("/favorites"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void 비회원의_찜하기_제출은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/10/favorite")).andExpect(status().isForbidden());
    }

    @Test
    void 찜하지_않은_회원의_상세_화면에는_찜하기_버튼만_보인다() throws Exception {
        mockMvc.perform(get("/restaurants/10").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("♡ 찜하기")))
                .andExpect(content().string(not(containsString("♥ 찜 취소"))));
    }

    @Test
    void 찜한_회원의_상세_화면에는_찜_취소_버튼과_찜_수가_보인다() throws Exception {
        when(restaurantService.detail(eq(RESTAURANT_ID), eq(MEMBER_ID))).thenReturn(detail(true, 5));

        mockMvc.perform(get("/restaurants/10").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("♥ 찜 취소")))
                .andExpect(content().string(containsString("찜 <span")))
                .andExpect(content().string(containsString(">5<")));
    }

    @Test
    void 비회원_상세_화면은_찜_버튼_대신_로그인_안내를_보인다() throws Exception {
        mockMvc.perform(get("/restaurants/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("찜하려면")))
                .andExpect(content().string(not(containsString("♡ 찜하기"))));
    }

    @Test
    void 로그인_회원이_찜하면_상세로_돌아가_찜_추가_안내를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromDetail(session);

        mockMvc.perform(post("/restaurants/10/favorite").session(session).param("_csrf", csrf))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/restaurants/10?favorite=added#favorite"));
        verify(favoriteService).favorite(RESTAURANT_ID, MEMBER_ID);
    }

    @Test
    void 이미_찜한_맛집을_다시_찜하면_중복_안내로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromDetail(session);
        when(favoriteService.favorite(eq(RESTAURANT_ID), eq(MEMBER_ID)))
                .thenThrow(new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS));

        mockMvc.perform(post("/restaurants/10/favorite").session(session).param("_csrf", csrf))
                .andExpect(redirectedUrl("/restaurants/10?favoriteError=duplicate#favorite"));
    }

    @Test
    void 상세에서_찜을_취소하면_상세로_돌아가_취소_안내를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromDetail(session);

        mockMvc.perform(post("/restaurants/10/favorite/delete").session(session).param("_csrf", csrf))
                .andExpect(redirectedUrl("/restaurants/10?favorite=removed#favorite"));
        verify(favoriteService).unfavorite(RESTAURANT_ID, MEMBER_ID);
    }

    @Test
    void 찜_목록에서_취소하면_같은_페이지로_돌아가_취소_안내를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromDetail(session);

        mockMvc.perform(post("/restaurants/10/favorite/delete").session(session).param("_csrf", csrf)
                        .param("from", "favorites").param("page", "2"))
                .andExpect(redirectedUrl("/favorites?page=2&favorite=removed"));
    }

    @Test
    void 찜_제출에_CSRF_토큰이_없으면_회원이라도_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/10/favorite").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/restaurants/10/favorite/delete").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 찜_목록_화면은_회원_본인_목록과_취소_버튼을_보여_준다() throws Exception {
        mockMvc.perform(get("/favorites").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내 찜 목록")))
                .andExpect(content().string(containsString("찜 맛집")))
                .andExpect(content().string(containsString("찜 취소")));
        verify(favoriteService).list(MEMBER_ID, 0, 12);
    }

    @Test
    void 찜이_없으면_빈_목록_안내를_보여_준다() throws Exception {
        when(favoriteService.list(eq(MEMBER_ID), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/favorites").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 찜한 맛집이 없습니다.")));
    }
}
