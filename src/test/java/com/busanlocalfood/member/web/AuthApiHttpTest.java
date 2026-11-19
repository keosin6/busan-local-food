package com.busanlocalfood.member.web;

import com.busanlocalfood.member.service.MemberAccountService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.LoginAttemptResult;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.refresh.RefreshTokenRotation;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.MemberLoginService;
import com.busanlocalfood.member.service.MemberRegistrationService;
import com.busanlocalfood.member.service.AuthApiService;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * API 인증 HTTP 엔드포인트의 MockMvc 테스트. 실제 SecurityConfig, JWT 설정, 쿠키 정책을 통과한다.
 * 서비스 경계(회원가입·로그인·Refresh·요청 제한·회원 조회)는 목으로 바꾼다. 흐름과 DB 동작은 AuthApiDbTest가 검증한다.
 */
@WebMvcTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
        RefreshTokenConfig.class, AuthApiService.class, AuthCookies.class, AuthController.class,
        AuthApiHttpTest.ClientIpConfig.class})
class AuthApiHttpTest {

    @MockitoBean
    private MemberAccountService memberAccountService;

    private static final String JWT_SECRET = randomBase64Key();
    private static final String ACCESS_RAW = "access-cookie-raw-value";
    private static final String REFRESH_RAW = "refresh-cookie-raw-value";

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private MemberRegistrationService registration;

    @MockitoBean
    private MemberLoginService loginService;

    @MockitoBean
    private RefreshTokenService refreshTokens;

    @MockitoBean
    private MemberRepository members;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    // ---- 픽스처 ----

    /** 실제 Member 엔티티를 만든다. 식별자는 테스트 전용으로 리플렉션에 설정한다. */
    private static Member member(long id, String email, String nickname, Role role, String hash, String googleSub) {
        Member member = Member.create(email, hash, nickname, googleSub);
        ReflectionTestUtils.setField(member, "id", id);
        if (role != Role.USER) {
            ReflectionTestUtils.setField(member, "role", role);
        }
        return member;
    }

    private static Member userMember() {
        return member(7L, "user@example.com", "부산먹보", Role.USER, "$2a$hash", null);
    }

    private static IssuedRefreshToken newRefresh(String raw) {
        return new IssuedRefreshToken(raw, LocalDateTime.now().plusDays(14));
    }

    private String validJwt() {
        return issuer.issue(7L, Role.USER).token();
    }

    /** API 체인의 CSRF 쿠키를 받는다. CsrfCookieFilter가 API 응답마다 XSRF-TOKEN을 내려준다. */
    private Cookie xsrf() throws Exception {
        return mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private ResultActions postJson(String path, String json, Cookie... cookies) throws Exception {
        Cookie xsrf = xsrf();
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)
                .cookie(withAll(xsrf, cookies)).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    private static Cookie[] withAll(Cookie xsrf, Cookie... extra) {
        Cookie[] all = new Cookie[extra.length + 1];
        all[0] = xsrf;
        System.arraycopy(extra, 0, all, 1, extra.length);
        return all;
    }

    private static String setCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
    }

    private static List<String> setCookies(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    private void stubLoginAttempt() {
        when(rateLimit.attemptLogin(anyString(), anyString(), any())).thenAnswer(invocation -> {
            BooleanSupplier credentials = invocation.getArgument(2);
            return credentials.getAsBoolean()
                    ? new LoginAttemptResult.Authenticated()
                    : new LoginAttemptResult.InvalidCredentials();
        });
    }

    // ---- 회원가입 POST /api/auth/signup ----

    @Test
    void 정상_가입은_201과_회원_정보를_반환하고_쿠키를_발급한다() throws Exception {
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        MvcResult result = postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("부산먹보"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();

        assertThat(setCookie(result, "access_token")).isNotBlank();
        assertThat(setCookie(result, "refresh_token")).startsWith("refresh_token=" + REFRESH_RAW);
    }

    @Test
    void 가입_응답_본문에는_토큰_원문이_없다() throws Exception {
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        MvcResult result = postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isCreated()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(REFRESH_RAW).doesNotContain("eyJ").doesNotContain("token");
    }

    @Test
    void 가입은_Spring_Security_세션을_만들지_않는다() throws Exception {
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        MvcResult result = postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isCreated()).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void 가입_쿠키는_HttpOnly_SameSite_Lax이고_Path와_만료가_정책과_같다() throws Exception {
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        MvcResult result = postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isCreated()).andReturn();

        String access = setCookie(result, "access_token");
        String refresh = setCookie(result, "refresh_token");
        assertThat(access).contains("Path=/api;", "HttpOnly", "SameSite=Lax", "Max-Age=1800");
        assertThat(refresh).contains("Path=/api/auth;", "HttpOnly", "SameSite=Lax", "Max-Age=1209600");
        assertThat(access + refresh).doesNotContain("Domain=").doesNotContain("Secure");
    }

    @Test
    void 가입_중복_이메일은_409_EMAIL_DUPLICATED다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATED));

        postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    void 가입_중복_닉네임은_409_NICKNAME_DUPLICATED다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED));

        postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    void 가입_입력_검증_실패는_400이다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        when(registration.register(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_INVALID));

        postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"short","passwordConfirm":"short","nickname":"부산먹보"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_INVALID"));
    }

    @Test
    void 가입_알_수_없는_필드는_400_VALIDATION_FAILED이고_가입하지_않는다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보","role":"ADMIN"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verify(registration, never()).register(any(), any(), any(), any());
    }

    @Test
    void 가입_잘못된_JSON은_400_VALIDATION_FAILED다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        postJson("/api/auth/signup", "{\"email\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 가입_Content_Type이_없으면_415다() throws Exception {
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());
        Cookie xsrf = xsrf();

        mockMvc.perform(post("/api/auth/signup").content("{}").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void 가입_CSRF_토큰이_없으면_403이고_가입하지_않는다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """)).andExpect(status().isForbidden());

        verify(registration, never()).register(any(), any(), any(), any());
    }

    @Test
    void 가입_요청_집계는_필터에서_한_번만_실행된다() throws Exception {
        when(registration.register(any(), any(), any(), any())).thenReturn(userMember());
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));
        when(rateLimit.recordSignupRequest(anyString())).thenReturn(new RateLimitDecision.Allowed());

        postJson("/api/auth/signup", """
                {"email":"user@example.com","password":"Passw0rd!","passwordConfirm":"Passw0rd!","nickname":"부산먹보"}
                """).andExpect(status().isCreated());

        verify(rateLimit, times(1)).recordSignupRequest(anyString());
    }

    // ---- 로그인 POST /api/auth/login ----

    @Test
    void 정상_로그인은_200과_쿠키와_만료_시간을_반환한다() throws Exception {
        stubLoginAttempt();
        when(loginService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.of(userMember()));
        when(refreshTokens.issueForNewLogin(7L)).thenReturn(newRefresh(REFRESH_RAW));

        MvcResult result = postJson("/api/auth/login", """
                {"email":"user@example.com","password":"Passw0rd!"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.nickname").value("부산먹보"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
                .andReturn();

        assertThat(setCookie(result, "access_token")).isNotBlank();
        assertThat(setCookie(result, "refresh_token")).startsWith("refresh_token=" + REFRESH_RAW);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(REFRESH_RAW);
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void 잘못된_비밀번호_미가입_이메일_Google_전용_계정은_같은_401_INVALID_CREDENTIALS다() throws Exception {
        stubLoginAttempt();
        when(loginService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.empty());

        for (String email : List.of("user@example.com", "unknown@example.com", "google-only@example.com")) {
            postJson("/api/auth/login", """
                    {"email":"%s","password":"Passw0rd!"}
                    """.formatted(email)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_CREDENTIALS.getMessage()));
        }
        verify(refreshTokens, never()).issueForNewLogin(anyLong());
    }

    @Test
    void 로그인_제한_초과는_429와_Retry_After이고_자격_증명을_확인하지_않는다() throws Exception {
        when(rateLimit.attemptLogin(anyString(), anyString(), any()))
                .thenReturn(new LoginAttemptResult.Limited(42));

        postJson("/api/auth/login", """
                {"email":"user@example.com","password":"Passw0rd!"}
                """).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        verify(loginService, never()).verifyCredentials(anyString(), anyString());
    }

    @Test
    void 로그인_제한_초과_응답은_쿠키를_설정하거나_삭제하지_않는다() throws Exception {
        when(rateLimit.attemptLogin(anyString(), anyString(), any()))
                .thenReturn(new LoginAttemptResult.Limited(42));

        MvcResult result = postJson("/api/auth/login", """
                {"email":"user@example.com","password":"Passw0rd!"}
                """).andExpect(status().isTooManyRequests()).andReturn();

        assertThat(setCookies(result)).isEmpty();
    }

    @Test
    void 로그인_입력_형식_오류는_400이고_제한_판정을_하지_않는다() throws Exception {
        postJson("/api/auth/login", """
                {"email":"not-an-email","password":"Passw0rd!"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_INVALID"));

        verify(rateLimit, never()).attemptLogin(anyString(), anyString(), any());
    }

    @Test
    void 로그인_알_수_없는_필드는_400_VALIDATION_FAILED다() throws Exception {
        postJson("/api/auth/login", """
                {"email":"user@example.com","password":"Passw0rd!","remember":true}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ---- 재발급 POST /api/auth/refresh ----

    @Test
    void 정상_재발급은_200과_새_쿠키를_발급한다() throws Exception {
        when(refreshTokens.rotate(anyString())).thenReturn(
                new RefreshTokenRotation.Rotated(7L, Role.USER, newRefresh("rotated-refresh-raw")));

        MvcResult result = postJson("/api/auth/refresh", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
                .andReturn();

        assertThat(setCookie(result, "refresh_token")).startsWith("refresh_token=rotated-refresh-raw");
        assertThat(setCookie(result, "access_token")).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("rotated-refresh-raw");
    }

    @Test
    void 재발급은_Access_Token_없이_쿠키만으로_동작한다() throws Exception {
        when(refreshTokens.rotate(anyString())).thenReturn(
                new RefreshTokenRotation.Rotated(7L, Role.USER, newRefresh("rotated-refresh-raw")));

        postJson("/api/auth/refresh", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠키가_없는_재발급은_401이고_두_JWT_쿠키를_만료시킨다() throws Exception {
        when(refreshTokens.rotate(any())).thenReturn(new RefreshTokenRotation.Rejected());

        MvcResult result = postJson("/api/auth/refresh", "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andReturn();

        assertExpired(result, "access_token", "/api");
        assertExpired(result, "refresh_token", "/api/auth");
    }

    @Test
    void 만료_위조_폐기된_재발급_토큰은_401이고_쿠키를_만료시킨다() throws Exception {
        when(refreshTokens.rotate(anyString())).thenReturn(new RefreshTokenRotation.Rejected());

        MvcResult result = postJson("/api/auth/refresh", "", new Cookie("refresh_token", "forged"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertExpired(result, "access_token", "/api");
        assertExpired(result, "refresh_token", "/api/auth");
    }

    @Test
    void 재사용_탐지는_401이고_두_JWT_쿠키를_만료시킨다() throws Exception {
        when(refreshTokens.rotate(anyString())).thenReturn(new RefreshTokenRotation.ReuseDetected(7L, 2));

        MvcResult result = postJson("/api/auth/refresh", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andReturn();

        assertExpired(result, "access_token", "/api");
        assertExpired(result, "refresh_token", "/api/auth");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("reuse").doesNotContain("ReuseDetected");
    }

    @Test
    void 재발급_제한_초과는_429이고_기존_쿠키를_건드리지_않는다() throws Exception {
        when(refreshTokens.rotate(anyString())).thenReturn(new RefreshTokenRotation.RateLimited(30));

        MvcResult result = postJson("/api/auth/refresh", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andReturn();

        assertThat(setCookies(result)).isEmpty();
    }

    // ---- 로그아웃 POST /api/auth/logout ----

    @Test
    void 로그아웃은_Refresh_Token을_폐기하고_두_쿠키를_만료시키며_200이다() throws Exception {
        MvcResult result = postJson("/api/auth/logout", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        verify(refreshTokens).logout(REFRESH_RAW);
        assertExpired(result, "access_token", "/api");
        assertExpired(result, "refresh_token", "/api/auth");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void 쿠키가_없는_로그아웃도_200이다() throws Exception {
        postJson("/api/auth/logout", "").andExpect(status().isOk());

        verify(refreshTokens).logout(null);
    }

    @Test
    void 로그아웃은_Access_Token_없이_호출할_수_있다() throws Exception {
        postJson("/api/auth/logout", "", new Cookie("refresh_token", REFRESH_RAW))
                .andExpect(status().isOk());
    }

    // ---- 내 정보 GET /api/auth/me ----

    @Test
    void 정상_JWT로_내_정보를_조회한다() throws Exception {
        when(members.findById(7L)).thenReturn(Optional.of(userMember()));

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("부산먹보"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.googleLinked").value(false))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void 내_정보_응답에는_비밀번호_해시와_Google_식별자가_없다() throws Exception {
        when(members.findById(7L)).thenReturn(Optional.of(member(7L, "user@example.com", "부산먹보",
                Role.USER, "$2a$secret-hash", "google-sub-secret")));

        MvcResult result = mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validJwt())))
                .andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("$2a$").doesNotContain("google-sub-secret")
                .doesNotContain("passwordHash").doesNotContain("googleSub").doesNotContain("refresh");
    }

    @Test
    void Google_연동_회원은_googleLinked가_true이고_hasPassword가_false다() throws Exception {
        when(members.findById(7L)).thenReturn(Optional.of(member(7L, "user@example.com", "부산먹보",
                Role.USER, null, "google-sub-value")));

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleLinked").value(true))
                .andExpect(jsonPath("$.data.hasPassword").value(false));
    }

    @Test
    void 위조된_JWT로_내_정보를_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", "not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void JWT가_없으면_내_정보_조회는_401이다() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 존재하지_않는_회원의_JWT는_401이다() throws Exception {
        when(members.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validJwt())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void 내_정보_조회는_세션을_만들지_않는다() throws Exception {
        when(members.findById(7L)).thenReturn(Optional.of(userMember()));

        MvcResult result = mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", validJwt())))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static void assertExpired(MvcResult result, String name, String path) {
        String header = setCookie(result, name);
        assertThat(header).contains("Max-Age=0").contains("Path=" + path + ";")
                .contains("HttpOnly").contains("SameSite=Lax");
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }
}
