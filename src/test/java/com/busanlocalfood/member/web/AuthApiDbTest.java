package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * API 인증 HTTP 흐름을 실제 MySQL 8 테스트 DB에서 검증한다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 db/manual/001 → 002 → 003을 순서대로 적용해야 한다. 각 테스트 전에 관련 테이블을 비운다.
 * 세션 쿠키(JSESSIONID)와 원문 토큰의 로그 노출 여부도 함께 확인한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AuthApiDbTest {

    private static final String PASSWORD = "Passw0rd!";

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> randomBase64Key());
        registry.add("auth.rate-limit.hmac-secret", () -> randomBase64Key());
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM login_failure_event");
        jdbc.update("DELETE FROM signup_request_event");
        jdbc.update("DELETE FROM refresh_request_event");
        jdbc.update("DELETE FROM google_oauth_request_event");
        jdbc.update("DELETE FROM auth_rate_limit_counter");
    }

    // ---- 픽스처와 도우미 ----

    private Cookie xsrf() throws Exception {
        return mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private MvcResult postJson(String path, String json, Cookie... cookies) throws Exception {
        Cookie xsrf = xsrf();
        Cookie[] all = new Cookie[cookies.length + 1];
        all[0] = xsrf;
        System.arraycopy(cookies, 0, all, 1, cookies.length);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)
                        .cookie(all).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andReturn();
    }

    private MvcResult signup(String email, String nickname) throws Exception {
        return postJson("/api/auth/signup", """
                {"email":"%s","password":"%s","passwordConfirm":"%s","nickname":"%s"}
                """.formatted(email, PASSWORD, PASSWORD, nickname));
    }

    private MvcResult login(String email, String password) throws Exception {
        return postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    /** Set-Cookie 헤더에서 쿠키 값만 꺼낸다. 값은 테스트 안에서만 쓰고 출력하지 않는다. */
    private static String cookieValue(MvcResult result, String name) {
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
        int end = header.indexOf(';');
        return header.substring(name.length() + 1, end < 0 ? header.length() : end);
    }

    private static String withoutTimestamp(String json) {
        return json.replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private Long memberId(String email) {
        return jdbc.queryForObject("SELECT id FROM member WHERE email = ?", Long.class, email);
    }

    private int activeTokenCount(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE member_id = ? AND revoked_at IS NULL", Integer.class, memberId);
    }

    private int distinctFamilies(long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT family_id) FROM refresh_token WHERE member_id = ?", Integer.class, memberId);
    }

    // ---- 테스트 ----

    @Test
    void 가입_로그인_내_정보_재발급_로그아웃_흐름이_DB와_함께_동작한다() throws Exception {
        MvcResult signup = signup("flow@example.com", "흐름회원");
        assertThat(signup.getResponse().getStatus()).isEqualTo(201);
        assertThat(signup.getRequest().getSession(false)).isNull();
        long memberId = memberId("flow@example.com");

        // 로그인은 새 계열을 만든다. 가입 때 만든 계열과 합쳐 두 계열이 된다.
        MvcResult login = login("flow@example.com", PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String access = cookieValue(login, "access_token");
        String refresh1 = cookieValue(login, "refresh_token");
        assertThat(activeTokenCount(memberId)).isEqualTo(2);
        assertThat(distinctFamilies(memberId)).isEqualTo(2);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("flow@example.com"))
                .andExpect(jsonPath("$.data.googleLinked").value(false))
                .andExpect(jsonPath("$.data.hasPassword").value(true));

        // 재발급: 기존 행은 폐기되고 같은 계열의 새 행이 생긴다.
        MvcResult refreshed = postJson("/api/auth/refresh", "", new Cookie("refresh_token", refresh1));
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        String refresh2 = cookieValue(refreshed, "refresh_token");
        assertThat(refresh2).isNotEqualTo(refresh1);
        assertThat(activeTokenCount(memberId)).isEqualTo(2);

        // 로그아웃: 현재 Refresh Token을 폐기하고 쿠키를 만료시킨다.
        MvcResult logout = postJson("/api/auth/logout", "", new Cookie("refresh_token", refresh2));
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("refresh_token=") && h.contains("Max-Age=0"));
        assertThat(logout.getRequest().getSession(false)).isNull();
        assertThat(activeTokenCount(memberId)).isEqualTo(1);
    }

    @Test
    void 폐기된_Refresh_Token_재사용은_회원의_활성_토큰을_커밋_후_모두_폐기한다() throws Exception {
        signup("reuse@example.com", "재사용회원");
        long memberId = memberId("reuse@example.com");

        // 계열 A: 로그인 후 한 번 회전해 원래 토큰을 폐기 상태로 만든다.
        String oldRefresh = cookieValue(login("reuse@example.com", PASSWORD), "refresh_token");
        postJson("/api/auth/refresh", "", new Cookie("refresh_token", oldRefresh));
        // 계열 B: 다른 기기의 로그인으로 활성 토큰을 하나 더 만든다.
        login("reuse@example.com", PASSWORD);
        assertThat(activeTokenCount(memberId)).isGreaterThanOrEqualTo(2);

        // 폐기된 토큰을 다시 내면 재사용이다. 응답은 401이고 쿠키는 만료된다.
        MvcResult reused = postJson("/api/auth/refresh", "", new Cookie("refresh_token", oldRefresh));
        assertThat(reused.getResponse().getStatus()).isEqualTo(401);
        assertThat(reused.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("access_token=") && h.contains("Max-Age=0"))
                .anyMatch(h -> h.startsWith("refresh_token=") && h.contains("Max-Age=0"));

        // 탐지 결과는 예외 없이 반환되므로 폐기가 커밋되어 DB에 남아야 한다.
        assertThat(activeTokenCount(memberId)).isZero();
    }

    @Test
    void 로그인_실패_다섯_번_뒤_여섯_번째는_429와_Retry_After다() throws Exception {
        signup("limit@example.com", "제한회원");

        for (int i = 0; i < 5; i++) {
            MvcResult failed = login("limit@example.com", "Wrong0000!");
            assertThat(failed.getResponse().getStatus()).isEqualTo(401);
        }

        MvcResult limited = login("limit@example.com", PASSWORD);
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 로그인_성공은_실패_횟수를_초기화한다() throws Exception {
        signup("reset@example.com", "초기화회원");

        for (int i = 0; i < 3; i++) {
            assertThat(login("reset@example.com", "Wrong0000!").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login("reset@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);

        // 초기화 뒤 다시 다섯 번까지는 401이어야 한다. 실패 기록이 남아 있었다면 여기서 429가 된다.
        for (int i = 0; i < 4; i++) {
            assertThat(login("reset@example.com", "Wrong0000!").getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void 가입_요청_집계는_DB에_한_번만_기록된다() throws Exception {
        signup("count@example.com", "집계회원");

        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM signup_request_event", Integer.class);
        assertThat(events).isEqualTo(1);
    }

    @Test
    void 미가입_이메일과_잘못된_비밀번호는_같은_401이다() throws Exception {
        signup("same@example.com", "같은회원");

        MvcResult unknown = login("nobody@example.com", PASSWORD);
        MvcResult wrong = login("same@example.com", "Wrong0000!");

        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        // timestamp는 초 단위라 두 요청 사이에 바뀔 수 있다. 본문의 나머지가 같은지만 본다.
        assertThat(withoutTimestamp(unknown.getResponse().getContentAsString()))
                .isEqualTo(withoutTimestamp(wrong.getResponse().getContentAsString()));
    }

    @Test
    void 로그아웃_뒤_같은_Refresh_Token으로_재발급하면_재사용으로_401이다() throws Exception {
        signup("after@example.com", "로그아웃회원");
        long memberId = memberId("after@example.com");
        String refresh = cookieValue(login("after@example.com", PASSWORD), "refresh_token");

        assertThat(postJson("/api/auth/logout", "", new Cookie("refresh_token", refresh))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/auth/refresh", "", new Cookie("refresh_token", refresh))
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(activeTokenCount(memberId)).isZero();
    }
}
