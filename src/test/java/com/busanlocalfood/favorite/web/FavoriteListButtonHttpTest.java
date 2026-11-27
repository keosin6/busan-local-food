package com.busanlocalfood.favorite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.review.service.ReviewService;
import com.busanlocalfood.review.service.ReviewSummaryService;
import com.busanlocalfood.web.HomeController;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
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
 * 맛집 목록 카드의 찜 버튼 (FUNCTION_SPEC F1-06). 비회원 상태, 회원의 찜 여부에 따른 버튼, 찜하기와 취소 후 복귀,
 * 검색·정렬·페이지 유지, CSRF, 목록 찜 상태 일괄 조회, 홈 화면 공유를 확인한다. 서비스는 목이다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = {RestaurantScreenController.class, FavoriteScreenController.class, HomeController.class},
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test-only"
        })
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class FavoriteListButtonHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminQueryService adminQueries;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminStorageRetryService adminStorageRetry;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.recommendation.service.RestaurantRecommendationService recommendations;

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String JWT_SECRET = randomBase64Key();
    private static final long MEMBER_ID = 7L;

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

    private static RestaurantSummary summary(long id, String name) {
        return new RestaurantSummary(id, name, new CategoryRef(1L, "한식"), "부산 해운대구",
                new BigDecimal("4.5"), 2, null, LocalDateTime.of(2026, 11, 2, 18, 30));
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.categories()).thenReturn(List.of());
        when(restaurantService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(summary(1L, "첫째 맛집"), summary(2L, "둘째 맛집")), 0, 12, 2, 1));
        when(favoriteService.favoritedAmong(eq(MEMBER_ID), anyList())).thenReturn(Set.of(1L));
        // 홈은 최신 맛집 목록을 쓴다 (F1-08).
        when(restaurantService.latest(anyInt())).thenReturn(
                List.of(summary(1L, "첫째 맛집"), summary(2L, "둘째 맛집")));
    }

    private String csrfFromPage(MockHttpSession session, String path) throws Exception {
        MvcResult page = mockMvc.perform(get(path).session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("목록 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @Test
    void 비회원_목록_카드는_찜_상태가_모두_false이고_로그인_링크만_보인다() throws Exception {
        mockMvc.perform(get("/restaurants"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("로그인하고 찜하기")))
                .andExpect(content().string(not(containsString("♡ 찜하기"))))
                .andExpect(content().string(not(containsString("♥ 찜 취소"))));
        verify(favoriteService, never()).favoritedAmong(anyLong(), anyList());
    }

    @Test
    void 로그인_회원_카드는_찜_여부에_따라_찜_취소와_찜하기_버튼을_나눠_보인다() throws Exception {
        mockMvc.perform(get("/restaurants").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/restaurants/1/favorite/delete\"")))
                .andExpect(content().string(containsString("action=\"/restaurants/2/favorite\"")))
                .andExpect(content().string(not(containsString("action=\"/restaurants/1/favorite\""))))
                .andExpect(content().string(not(containsString("action=\"/restaurants/2/favorite/delete\""))));
        verify(favoriteService).favoritedAmong(MEMBER_ID, List.of(1L, 2L));
    }

    @Test
    void 목록_찜_상태는_한_번의_일괄_조회로_구한다() throws Exception {
        mockMvc.perform(get("/restaurants").session(loggedInSession(MEMBER_ID))).andExpect(status().isOk());

        verify(favoriteService, org.mockito.Mockito.times(1)).favoritedAmong(eq(MEMBER_ID), anyList());
    }

    @Test
    void 카드_찜하기_후_검색_조건과_페이지를_유지한_목록으로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromPage(session, "/restaurants");

        mockMvc.perform(post("/restaurants/2/favorite").session(session).param("_csrf", csrf)
                        .param("from", "restaurants").param("category", "3").param("keyword", "해운대")
                        .param("sort", "rating").param("page", "2").param("size", "12"))
                .andExpect(redirectedUrl(
                        "/restaurants?category=3&keyword=%ED%95%B4%EC%9A%B4%EB%8C%80&sort=rating&size=12&page=2"
                                + "&favorite=added#restaurant-2"));
        verify(favoriteService).favorite(2L, MEMBER_ID);
    }

    @Test
    void 카드_찜_취소_후_홈_화면으로_돌아간다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromPage(session, "/");

        mockMvc.perform(post("/restaurants/1/favorite/delete").session(session).param("_csrf", csrf)
                        .param("from", "home").param("page", "0").param("size", "12"))
                .andExpect(redirectedUrl("/?favorite=removed#restaurant-1"));
        verify(favoriteService).unfavorite(1L, MEMBER_ID);
    }

    @Test
    void 카드에서_중복_찜하기는_목록으로_돌아가_안내한다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromPage(session, "/restaurants");
        when(favoriteService.favorite(eq(2L), eq(MEMBER_ID)))
                .thenThrow(new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS));

        mockMvc.perform(post("/restaurants/2/favorite").session(session).param("_csrf", csrf)
                        .param("from", "restaurants").param("page", "0").param("size", "12"))
                .andExpect(redirectedUrl("/restaurants?size=12&page=0&favoriteError=duplicate#restaurant-2"));
    }

    @Test
    void 목록_카드_찜_제출에_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/restaurants/2/favorite").session(loggedInSession(MEMBER_ID))
                        .param("from", "restaurants"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/restaurants/1/favorite/delete").session(loggedInSession(MEMBER_ID))
                        .param("from", "restaurants"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 허용되지_않은_복귀_값은_맛집_상세로_보낸다_열린_리다이렉트를_막는다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromPage(session, "/restaurants");

        mockMvc.perform(post("/restaurants/2/favorite").session(session).param("_csrf", csrf)
                        .param("from", "https://evil.example/steal"))
                .andExpect(redirectedUrl("/restaurants/2?favorite=added#favorite"));
    }

    @Test
    void 검색_정렬_페이지_조건이_맛집_서비스에_그대로_전달된다() throws Exception {
        mockMvc.perform(get("/restaurants").session(loggedInSession(MEMBER_ID))
                        .param("keyword", "해운대").param("sort", "rating").param("page", "2").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("첫째 맛집")));
        verify(restaurantService).list(null, "해운대", "rating", 2, 12);
    }

    @Test
    void 목록_찜_안내_문구를_보여_준다() throws Exception {
        mockMvc.perform(get("/restaurants").session(loggedInSession(MEMBER_ID)).param("favorite", "removed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("찜을 취소했습니다.")));
    }

    @Test
    void 홈_화면의_카드도_같은_찜_버튼을_보이고_돌아갈_곳을_홈으로_표시한다() throws Exception {
        mockMvc.perform(get("/").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("♥ 찜 취소")))
                .andExpect(content().string(containsString("name=\"from\" value=\"home\"")));
    }
}
