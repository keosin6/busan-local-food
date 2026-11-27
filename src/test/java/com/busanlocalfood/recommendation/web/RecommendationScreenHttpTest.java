package com.busanlocalfood.recommendation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import com.busanlocalfood.recommendation.dto.RecommendationDtos.Recommendation;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RestaurantRecommendationService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * AI 추천 화면 (FUNCTION_SPEC F1-07, /recommend). 세션과 CSRF를 쓴다. 서비스는 목이다.
 * 확인: 비로그인 차단, 로그인 화면, 결과 카드, 후보 없음 안내, AI 실패의 재시도 안내, 취향 검증 안내, CSRF.
 */
@WebMvcTest(controllers = RecommendationScreenController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class RecommendationScreenHttpTest {

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
    private RestaurantRecommendationService recommendations;

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

    private static RecommendationResponse withOne() {
        return new RecommendationResponse(List.of(new Recommendation(10L, "해운대 돼지국밥", "한식",
                new BigDecimal("4.5"), "진한 국물이 취향에 맞습니다.")), 42, null);
    }

    private String csrfFromForm(MockHttpSession session) throws Exception {
        MvcResult page = mockMvc.perform(get("/recommend").session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("추천 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @Test
    void 비로그인은_화면에_들어갈_수_없고_로그인으로_보낸다() throws Exception {
        mockMvc.perform(get("/recommend"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(recommendations);
    }

    @Test
    void 비로그인_추천_제출은_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/recommend").param("preference", "국밥")).andExpect(status().isForbidden());
        verifyNoInteractions(recommendations);
    }

    @Test
    void 로그인_회원은_입력_화면을_본다() throws Exception {
        mockMvc.perform(get("/recommend").session(loggedInSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"preference\"")))
                .andExpect(content().string(containsString("추천 받기")))
                .andExpect(content().string(containsString("1~200자")));
    }

    @Test
    void 취향을_입력하면_결과_카드를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromForm(session);
        when(recommendations.recommend(eq("조용한 국밥"))).thenReturn(withOne());

        mockMvc.perform(post("/recommend").session(session).param("_csrf", csrf).param("preference", "조용한 국밥"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("해운대 돼지국밥")))
                .andExpect(content().string(containsString("진한 국물이 취향에 맞습니다.")))
                .andExpect(content().string(containsString("/restaurants/10")))
                .andExpect(content().string(containsString("후보 42곳 중에서 골랐습니다.")))
                .andExpect(content().string(containsString("4.5")));
    }

    @Test
    void 후보가_없으면_안내_문구를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromForm(session);
        when(recommendations.recommend(any())).thenReturn(new RecommendationResponse(List.of(), 0,
                "등록된 맛집이 없어 추천할 수 없습니다."));

        mockMvc.perform(post("/recommend").session(session).param("_csrf", csrf).param("preference", "국밥"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 맛집이 없어 추천할 수 없습니다.")))
                .andExpect(content().string(not(containsString("맛집 상세 보기"))));
    }

    @Test
    void AI_호출이_실패하면_다시_시도하라고_안내한다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromForm(session);
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(post("/recommend").session(session).param("_csrf", csrf).param("preference", "국밥"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 추천을 가져오지 못했습니다.")))
                .andExpect(content().string(containsString("다시 눌러 주세요")));
    }

    @Test
    void AI_응답이_잘못되면_다시_시도하라고_안내한다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromForm(session);
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.AI_RESPONSE_INVALID));

        mockMvc.perform(post("/recommend").session(session).param("_csrf", csrf).param("preference", "국밥"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 추천 결과를 처리하지 못했습니다.")));
    }

    @Test
    void 취향이_비었거나_길면_입력_안내를_보여_준다() throws Exception {
        MockHttpSession session = loggedInSession(MEMBER_ID);
        String csrf = csrfFromForm(session);
        when(recommendations.recommend(any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED));

        mockMvc.perform(post("/recommend").session(session).param("_csrf", csrf).param("preference", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("취향은 1~200자로 입력해 주세요.")));
    }

    @Test
    void 로그인_회원도_CSRF_토큰이_없으면_추천_제출이_403이다() throws Exception {
        mockMvc.perform(post("/recommend").session(loggedInSession(MEMBER_ID)).param("preference", "국밥"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recommendations);
    }
}
