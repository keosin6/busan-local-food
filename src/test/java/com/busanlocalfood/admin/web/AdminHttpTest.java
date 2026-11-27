package com.busanlocalfood.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.admin.dto.AdminDtos.AdminMemberItem;
import com.busanlocalfood.admin.dto.AdminDtos.Dashboard;
import com.busanlocalfood.admin.dto.AdminDtos.OwnerContact;
import com.busanlocalfood.admin.dto.AdminDtos.AdminRestaurantItem;
import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.admin.service.AdminQueryService;
import com.busanlocalfood.admin.service.AdminStorageRetryService;
import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.service.ReviewService;
import jakarta.servlet.http.Cookie;
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
 * 관리자 접근 제어와 화면 (FUNCTION_SPEC F1-09). 실제 SecurityConfig와 JWT 검증을 쓴다. 서비스는 목이다.
 * 확인: 화면은 비로그인 로그인 이동, USER 403, ADMIN 정상. API는 비로그인 401, USER 403 JSON, ADMIN 정상.
 * role 파라미터와 알 수 없는 필드로 권한이 올라가지 않는지, 화면 POST의 CSRF, 화면 렌더링과 민감 정보 미노출을 본다.
 * 데이터베이스 없이 실행한다.
 */
@WebMvcTest(controllers = {AdminApiController.class, AdminRestaurantApiController.class, AdminScreenController.class},
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test-only"
        })
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class})
class AdminHttpTest {

    private static final String SESSION_CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");
    private static final String JWT_SECRET = randomBase64Key();
    private static final long ADMIN_ID = 9L;
    private static final long USER_ID = 7L;

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
    private AdminQueryService queries;

    @MockitoBean
    private AdminStorageRetryService storageRetry;

    @MockitoBean
    private RestaurantService restaurants;

    @MockitoBean
    private RestaurantImageService images;

    @MockitoBean
    private ReviewService reviews;

    @MockitoBean
    private MemberAccountService memberAccountService;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private static MockHttpSession session(long memberId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_CONTEXT, new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
        return session;
    }

    private String bearer(long memberId, Role role) {
        return "Bearer " + issuer.issue(memberId, role).token();
    }

    private Cookie xsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/categories")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private String csrfFrom(MockHttpSession session, String path) throws Exception {
        MvcResult page = mockMvc.perform(get(path).session(session)).andReturn();
        Matcher matcher = CSRF_FIELD.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).as("관리자 화면의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    @BeforeEach
    void stubs() {
        when(queries.dashboard(any())).thenReturn(new Dashboard(12, 34, 56, 3, 4, 5));
        when(queries.restaurants(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(new AdminRestaurantItem(10L, "관리 대상 맛집", new CategoryRef(1L, "한식"),
                        "부산 해운대구", new BigDecimal("4.0"), 2, 1,
                        new OwnerContact(3L, "작성자닉", "owner@example.test"),
                        LocalDateTime.of(2026, 11, 2, 18, 30))), 0, 20, 1, 1));
        when(queries.member(any(), anyLong())).thenReturn(new AdminMemberItem(3L, "member@example.test", "회원닉",
                "USER", true, 1, 2, LocalDateTime.of(2026, 11, 1, 9, 0)));
        when(queries.members(any(), any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(new AdminMemberItem(3L, "member@example.test", "회원닉", "USER", true,
                        1, 2, LocalDateTime.of(2026, 11, 1, 9, 0))), 0, 20, 1, 1));
        when(storageRetry.retry(any())).thenReturn(new RetryResult(2, 1, 1));
        when(restaurants.categories()).thenReturn(List.of(new CategoryResponse(1L, "한식", 1)));
        when(queries.reviews(any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(queries.storageDeletions(any(), anyInt(), anyInt())).thenReturn(
                new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    // 화면 접근 (SecurityConfig /admin/**)

    @Test
    void 비로그인은_관리자_화면에서_로그인으로_보낸다() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void USER는_관리자_화면에_403이다() throws Exception {
        mockMvc.perform(get("/admin").session(session(USER_ID, "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN은_관리자_대시보드를_본다() throws Exception {
        mockMvc.perform(get("/admin").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자 대시보드")))
                .andExpect(content().string(containsString(">34<")));
    }

    @Test
    void 관리자_맛집_화면은_작성자와_이미지_수를_보여_준다() throws Exception {
        mockMvc.perform(get("/admin/restaurants").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리 대상 맛집")))
                .andExpect(content().string(containsString("owner@example.test")))
                .andExpect(content().string(containsString("/admin/restaurants/10/edit")));
    }

    @Test
    void 회원_조회_화면은_조회만_하고_역할_변경_버튼이_없다() throws Exception {
        mockMvc.perform(get("/admin/members").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("member@example.test")))
                .andExpect(content().string(not(containsString(">역할 변경<"))))
                .andExpect(content().string(not(containsString(">정지<"))))
                .andExpect(content().string(not(containsString(">탈퇴<"))))
                .andExpect(content().string(not(containsString("name=\"role\""))));
    }

    @Test
    void 회원_상세_화면에는_비밀번호_해시와_Google_식별값이_없다() throws Exception {
        mockMvc.perform(get("/admin/members/3").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("member@example.test")))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("googleSub"))));
    }

    @Test
    void 관리자_화면_POST는_CSRF_토큰이_없으면_403이다() throws Exception {
        mockMvc.perform(post("/admin/restaurants/10/delete").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/storage-deletions/retry").session(session(ADMIN_ID, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(restaurants, org.mockito.Mockito.never()).deleteAsAdmin(anyLong(), any());
    }

    @Test
    void 관리자_재시도_화면_POST는_결과를_플래시로_보여_주고_목록으로_돌아간다() throws Exception {
        MockHttpSession session = session(ADMIN_ID, "ADMIN");
        String csrf = csrfFrom(session, "/admin/storage-deletions");

        mockMvc.perform(post("/admin/storage-deletions/retry").session(session).param("_csrf", csrf))
                .andExpect(redirectedUrl("/admin/storage-deletions"));
        verify(storageRetry).retry(any());
    }

    // API 접근 (SecurityConfig /api/admin/**)

    @Test
    void 비로그인_관리자_API는_401이다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/storage-deletions/retry").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void USER_토큰의_관리자_API는_403_FORBIDDEN_JSON이다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(USER_ID, Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void USER가_역할_파라미터로_ADMIN을_주장해도_403이다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").param("role", "ADMIN")
                        .header("Authorization", bearer(USER_ID, Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_토큰의_관리자_API는_정상_응답한다() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(ADMIN_ID, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberCount").value(12))
                .andExpect(jsonPath("$.data.pendingStorageDeletionCount").value(5));
    }

    @Test
    void 관리자_맛집_수정에_작성자_필드를_보내면_400이다() throws Exception {
        String body = "{\"name\":\"이름\",\"categoryId\":1,\"address\":\"주소\",\"description\":\"설명\","
                + "\"owner\":{\"memberId\":7}}";

        mockMvc.perform(put("/api/admin/restaurants/10").header("Authorization", bearer(ADMIN_ID, Role.ADMIN))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verify(restaurants, org.mockito.Mockito.never()).updateAsAdmin(anyLong(), any(), any());
    }

    @Test
    void 관리자_맛집_삭제_API는_USER에게_403이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(delete("/api/admin/restaurants/10").header("Authorization", bearer(USER_ID, Role.USER)))
                .andExpect(status().isForbidden());
        verify(restaurants, org.mockito.Mockito.never()).deleteAsAdmin(anyLong(), any());
    }

    @Test
    void 관리자_재시도_API는_결과_집계를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/admin/storage-deletions/retry")
                        .header("Authorization", bearer(ADMIN_ID, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(1));
    }

    @Test
    void 관리자_API_오류_본문에는_예외_메시지나_권한_이름이_없다() throws Exception {
        mockMvc.perform(get("/api/admin/members").header("Authorization", bearer(USER_ID, Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("ROLE_ADMIN"))))
                .andExpect(content().string(not(containsString("hasRole"))));
    }

    @Test
    void 관리자_API_응답에는_비밀번호_해시가_없다() throws Exception {
        mockMvc.perform(get("/api/admin/members").header("Authorization", bearer(ADMIN_ID, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("googleSub"))))
                .andExpect(header().string("Set-Cookie", not(containsString("JSESSIONID"))));
    }

    @Test
    void 회원_역할_변경_경로는_없다() throws Exception {
        Cookie xsrf = xsrfCookie();

        mockMvc.perform(post("/api/admin/members/3/role").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .header("Authorization", bearer(ADMIN_ID, Role.ADMIN))
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/admin/members/3").header("Authorization", bearer(ADMIN_ID, Role.ADMIN))
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().is4xxClientError());
    }
}
