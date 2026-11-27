package com.busanlocalfood.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
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

/**
 * 공개 메인 홈 (FUNCTION_SPEC F1-08). 비회원 접근, 최신 맛집 순서와 상세 링크, 카테고리 탐색 링크, 검색 진입,
 * 빈 상태, 이미지 없는 카드, 회원의 찜 버튼 상태, 반응형 그리드, 찜 안내 문구를 확인한다. 서비스는 목이다. 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = HomeController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class HomePageHttpTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminQueryService adminQueries;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.busanlocalfood.admin.service.AdminStorageRetryService adminStorageRetry;

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final long MEMBER_ID = 7L;
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

    private static RestaurantSummary summary(long id, String name, String thumbnail) {
        return new RestaurantSummary(id, name, new CategoryRef(1L, "한식"), "부산 해운대구",
                new BigDecimal("4.5"), 2, thumbnail, LocalDateTime.of(2026, 11, 2, 18, 30));
    }

    @BeforeEach
    void stubs() {
        when(restaurantService.latest(anyInt())).thenReturn(List.of());
        when(restaurantService.categories()).thenReturn(List.of(
                new CategoryResponse(1L, "한식", 1), new CategoryResponse(2L, "중식", 2)));
    }

    @Test
    void 비회원도_홈에_접근하고_검색_진입과_카테고리_탐색을_본다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("부산 맛집을 찾아보세요")))
                .andExpect(content().string(containsString("action=\"/restaurants\"")))
                .andExpect(content().string(containsString("name=\"keyword\"")))
                .andExpect(content().string(containsString("href=\"/restaurants?category=1\"")))
                .andExpect(content().string(containsString("href=\"/restaurants?category=2\"")));
        verify(restaurantService).latest(8);
    }

    @Test
    void 최신_맛집은_서비스가_준_순서대로_상세_링크와_함께_보인다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(
                summary(30L, "가장 최근 맛집", null), summary(20L, "그 다음 맛집", null)));

        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("href=\"/restaurants/30\"").contains("href=\"/restaurants/20\"");
        assertThat(html.indexOf("가장 최근 맛집")).isLessThan(html.indexOf("그 다음 맛집"));
    }

    @Test
    void 맛집이_없으면_빈_상태_안내를_보이고_카테고리는_그대로_보인다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 등록된 맛집이 없습니다.")))
                .andExpect(content().string(containsString("href=\"/restaurants?category=1\"")));
    }

    @Test
    void 이미지가_없는_맛집은_사진_없음_자리표시로_보인다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(summary(5L, "사진 없는 맛집", null)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사진 없음")))
                .andExpect(content().string(containsString("사진 없는 맛집")));
    }

    @Test
    void 대표_이미지가_있으면_그_주소를_쓴다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(
                summary(6L, "사진 있는 맛집", "https://img.example.test/a.jpg")));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"https://img.example.test/a.jpg\"")));
    }

    @Test
    void 비회원_카드에는_찜_폼이_없고_로그인_링크만_보인다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(summary(1L, "맛집", null)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("로그인하고 찜하기")))
                .andExpect(content().string(not(containsString("action=\"/restaurants/1/favorite\""))));
        verify(favoriteService, org.mockito.Mockito.never()).favoritedAmong(org.mockito.ArgumentMatchers.anyLong(),
                anyList());
    }

    @Test
    void 로그인_회원_카드는_찜_여부에_따라_버튼이_나뉜다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(summary(1L, "찜한 맛집", null),
                summary(2L, "안 찜한 맛집", null)));
        when(favoriteService.favoritedAmong(eq(MEMBER_ID), anyList())).thenReturn(Set.of(1L));

        mockMvc.perform(get("/").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/restaurants/1/favorite/delete\"")))
                .andExpect(content().string(containsString("action=\"/restaurants/2/favorite\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void 반응형_그리드_클래스를_쓴다() throws Exception {
        when(restaurantService.latest(anyInt())).thenReturn(List.of(summary(1L, "맛집", null)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("row-cols-1")))
                .andExpect(content().string(containsString("row-cols-sm-2")))
                .andExpect(content().string(containsString("row-cols-lg-4")));
    }

    @Test
    void 찜_처리_뒤_안내_문구를_보여_준다() throws Exception {
        mockMvc.perform(get("/").param("favorite", "added"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("찜 목록에 추가했습니다.")));
    }
}
