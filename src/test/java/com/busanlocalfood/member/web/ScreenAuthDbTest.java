package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 화면 인증 흐름을 실제 MySQL 8 테스트 DB에서 검증한다 (API_SPEC 2.9~2.11, T12).
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 db/manual/001 → 002 → 003을 순서대로 적용해야 한다. 각 테스트 전에 관련 테이블을 비운다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ScreenAuthDbTest {

    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern CSRF_FIELD = Pattern.compile("\"_csrf\"[^>]*?value=\"([^\"]+)\"");

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

    @Autowired
    private MemberRepository members;

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

    // ---- 도우미 ----

    private record Screen(MockHttpSession session, String csrf) {
    }

    private Screen screen(String path, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(path).session(session == null ? new MockHttpSession() : session))
                .andReturn();
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return new Screen((MockHttpSession) result.getRequest().getSession(false), matcher.group(1));
    }

    private MvcResult screenSignup(String email, String nickname, String password) throws Exception {
        Screen page = screen("/signup", null);
        return mockMvc.perform(post("/signup").session(page.session()).param("_csrf", page.csrf())
                        .param("email", email).param("password", password)
                        .param("passwordConfirm", password).param("nickname", nickname))
                .andReturn();
    }

    private MvcResult screenLogin(String email, String password, MockHttpSession session, String csrf)
            throws Exception {
        return mockMvc.perform(post("/login").session(session).param("_csrf", csrf)
                        .param("email", email).param("password", password))
                .andReturn();
    }

    private Cookie xsrf() throws Exception {
        return mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private static String cookieValue(MvcResult result, String name) {
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
        int end = header.indexOf(';');
        return header.substring(name.length() + 1, end < 0 ? header.length() : end);
    }

    private int activeTokens(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token rt JOIN member m ON m.id = rt.member_id "
                + "WHERE m.email = ? AND rt.revoked_at IS NULL", Integer.class, email);
    }

    // ---- 테스트 ----

    @Test
    void 화면_가입은_회원과_Refresh_Token_해시를_DB에_남기고_세션과_JWT_쿠키를_만든다() throws Exception {
        MvcResult result = screenSignup("screen@example.com", "화면회원", PASSWORD);

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE email = 'screen@example.com'",
                Integer.class);
        assertThat(members).isEqualTo(1);
        assertThat(activeTokens("screen@example.com")).isEqualTo(1);

        // 원문은 DB에 없고 SHA-256 해시(64자)만 있다.
        String raw = cookieValue(result, "refresh_token");
        Integer rawInDb = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?",
                Integer.class, raw);
        assertThat(rawInDb).isZero();
        Integer hashLength = jdbc.queryForObject("SELECT LENGTH(token_hash) FROM refresh_token LIMIT 1", Integer.class);
        assertThat(hashLength).isEqualTo(64);
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void 화면_가입_검증_실패는_회원을_만들지_않는다() throws Exception {
        MvcResult result = screenSignup("weak@example.com", "약한회원", "short");

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/signup?error=validation_failed");
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
        assertThat(members).isZero();
    }

    @Test
    void 화면_가입_중복_이메일은_회원을_추가하지_않는다() throws Exception {
        screenSignup("dup@example.com", "첫회원", PASSWORD);

        MvcResult second = screenSignup("dup@example.com", "둘째회원", PASSWORD);

        assertThat(second.getResponse().getRedirectedUrl()).isEqualTo("/signup?error=email_duplicated");
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
        assertThat(members).isEqualTo(1);
    }

    @Test
    void 화면_로그인_실패_다섯_번_뒤_여섯_번째는_429이고_실패는_다섯_건만_기록된다() throws Exception {
        screenSignup("limit@example.com", "제한회원", PASSWORD);

        for (int i = 0; i < 5; i++) {
            Screen page = screen("/login", null);
            MvcResult failed = screenLogin("limit@example.com", "Wrong0000!", page.session(), page.csrf());
            assertThat(failed.getResponse().getRedirectedUrl()).isEqualTo("/login?error=invalid_credentials");
        }
        Screen page = screen("/login", null);
        MvcResult limited = screenLogin("limit@example.com", PASSWORD, page.session(), page.csrf());

        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        Integer failures = jdbc.queryForObject("SELECT COUNT(*) FROM login_failure_event", Integer.class);
        assertThat(failures).isEqualTo(5);
    }

    @Test
    void Google_전용_계정은_미가입_이메일과_같은_실패_리다이렉트를_받는다() throws Exception {
        members.save(Member.create("google-only@example.com", null, "구글회원", "google-sub-1"));

        Screen first = screen("/login", null);
        String googleOnly = screenLogin("google-only@example.com", PASSWORD, first.session(), first.csrf())
                .getResponse().getRedirectedUrl();
        Screen second = screen("/login", null);
        String unknown = screenLogin("nobody@example.com", PASSWORD, second.session(), second.csrf())
                .getResponse().getRedirectedUrl();

        assertThat(googleOnly).isEqualTo("/login?error=invalid_credentials");
        assertThat(googleOnly).isEqualTo(unknown);
    }

    @Test
    void 화면_로그인_후_JWT만으로_화면에_들어가면_로그인_상태가_아니다() throws Exception {
        screenSignup("jwt-only@example.com", "토큰회원", PASSWORD);
        Screen page = screen("/login", null);
        MvcResult login = screenLogin("jwt-only@example.com", PASSWORD, page.session(), page.csrf());
        String access = cookieValue(login, "access_token");

        // API JWT는 API에서 인증된다.
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", access)))
                .andExpect(status().isOk());
        // 같은 JWT를 화면 요청에 실어도 로그인된 화면 사용자가 되지 않는다.
        String home = mockMvc.perform(get("/").cookie(new Cookie("access_token", access)))
                .andReturn().getResponse().getContentAsString();
        assertThat(home).doesNotContain("id=\"logout-form\"");
    }

    @Test
    void 로그아웃_T12_순서는_API_토큰을_먼저_폐기하고_화면_세션을_끊으며_재시도해도_안전하다() throws Exception {
        MvcResult signup = screenSignup("flow@example.com", "흐름회원", PASSWORD);
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession(false);
        String access = cookieValue(signup, "access_token");
        String refresh = cookieValue(signup, "refresh_token");
        assertThat(activeTokens("flow@example.com")).isEqualTo(1);

        // 1) API 로그아웃: Refresh Token 폐기.
        Cookie xsrf = xsrf();
        mockMvc.perform(post("/api/auth/logout").session(session)
                        .cookie(xsrf, new Cookie("access_token", access), new Cookie("refresh_token", refresh))
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        assertThat(activeTokens("flow@example.com")).isZero();

        // 2) 화면 로그아웃: 세션 무효화, JSESSIONID 만료.
        Screen home = screen("/", session);
        MvcResult screenOut = mockMvc.perform(post("/logout").session(session).param("_csrf", home.csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/login"))
                .andReturn();
        assertThat(session.isInvalid()).isTrue();
        assertThat(screenOut.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(h -> h.startsWith("JSESSIONID=") && h.contains("Max-Age=0"));

        // 3) 재시도: 이미 로그아웃된 상태에서도 안전하다.
        // 승인이 없는 새 세션의 화면 로그아웃은 거부된다. 세션은 그대로 남는다.
        Screen after = screen("/login", null);
        mockMvc.perform(post("/logout").session(after.session()).param("_csrf", after.csrf()))
                .andExpect(status().isForbidden());
        assertThat(after.session().isInvalid()).isFalse();
        xsrf = xsrf();
        mockMvc.perform(post("/api/auth/logout").cookie(xsrf, new Cookie("refresh_token", refresh))
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        assertThat(activeTokens("flow@example.com")).isZero();
    }

    @Test
    void API_로그아웃이_실패하면_DB_토큰은_그대로_남고_화면_로그아웃도_거부된다() throws Exception {
        MvcResult signup = screenSignup("guard@example.com", "가드회원", PASSWORD);
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession(false);
        String refresh = cookieValue(signup, "refresh_token");

        // XSRF 헤더가 없어 API 로그아웃이 403으로 실패한다.
        mockMvc.perform(post("/api/auth/logout").session(session)
                        .cookie(new Cookie("refresh_token", refresh)))
                .andExpect(status().isForbidden());
        assertThat(activeTokens("guard@example.com")).isEqualTo(1);

        Screen home = screen("/", session);
        mockMvc.perform(post("/logout").session(session).param("_csrf", home.csrf()))
                .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        assertThat(activeTokens("guard@example.com")).isEqualTo(1);
    }
}
