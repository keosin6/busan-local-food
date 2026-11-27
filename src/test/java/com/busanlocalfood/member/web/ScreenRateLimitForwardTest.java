package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.config.SecurityConfig;
import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.GlobalExceptionHandler;
import com.busanlocalfood.member.jwt.JwtConfig;
import com.busanlocalfood.member.oauth.GoogleOAuth2Config;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.refresh.RefreshTokenConfig;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleSignupService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.web.HomeController;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 화면 요청 제한 안내의 실제 forward 처리 (API_SPEC 1.4). MockMvc는 forward를 기록만 하므로,
 * 내장 Tomcat에서 실제 HTTP 요청으로 DispatcherServlet이 /rate-limited를 렌더링하는지 확인한다.
 * 데이터베이스 없이 실행한다. 회원·인증·요청 제한 서비스는 목이다.
 */
@SpringBootTest(classes = ScreenRateLimitForwardTest.ForwardTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // 전역 DB 환경변수 검사(ApplicationEnvironmentPrepared 단계)를 통과시키는 더미 값이다.
                // DataSource 자동 구성을 제외하므로 실제 연결은 일어나지 않는다.
                "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unused",
                "spring.datasource.username=unused",
                "spring.datasource.password=unused",
                // 이 테스트는 AI를 쓰지 않는다. 스타터의 채팅 자동 구성이 OpenRouter 키 없이 만들어지지 않도록 끈다.
                "spring.ai.model.chat=none"
        })
class ScreenRateLimitForwardTest {

    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String FORM = "application/x-www-form-urlencoded";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> randomBase64Key());
        registry.add("app.oauth2.google.client-id", () -> "forward-test-client");
        registry.add("app.oauth2.google.client-secret", () -> "forward-test-secret");
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @LocalServerPort
    private int port;

    @MockitoBean
    private AuthRateLimitService rateLimit;

    @MockitoBean
    private AuthApiService authApi;

    @MockitoBean
    private com.busanlocalfood.restaurant.service.RestaurantService restaurantService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewService reviewService;

    @MockitoBean
    private com.busanlocalfood.review.service.ReviewSummaryService reviewSummaryService;

    @MockitoBean
    private com.busanlocalfood.favorite.service.FavoriteService favoriteService;

    @MockitoBean
    private MemberAccountService accounts;

    @MockitoBean
    private GoogleLoginService googleLoginService;

    @MockitoBean
    private GoogleSignupService googleSignupService;

    private CookieManager cookies;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        when(rateLimit.recordSignupRequest(any())).thenReturn(new RateLimitDecision.Limited(42));
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(77));
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String contentType, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 화면 폼을 한 번 열어 세션과 CSRF 토큰을 얻는다. */
    private String screenCsrf() throws IOException, InterruptedException {
        HttpResponse<String> page = get("/signup");
        assertThat(page.statusCode()).isEqualTo(200);
        Matcher matcher = CSRF_FIELD.matcher(page.body());
        assertThat(matcher.find()).as("회원가입 폼의 CSRF 필드").isTrue();
        return matcher.group(1);
    }

    private Optional<String> sessionCookie() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID"))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private static void assertScreen429(HttpResponse<String> response, String retryAfter) {
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).contains(retryAfter);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html");
        assertThat(response.body()).contains("<!DOCTYPE html>");
        assertThat(response.body()).contains("요청이 너무 많습니다");
        assertThat(response.body()).contains("<title>요청 제한");
        assertThat(response.body()).doesNotContain("\"error\"");
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
    }

    @Test
    void POST_회원가입_제한_초과는_실제_forward되어_429_HTML_안내_페이지를_보인다() throws Exception {
        String csrf = screenCsrf();
        String session = sessionCookie().orElseThrow();

        HttpResponse<String> response = post("/signup", FORM,
                "_csrf=" + csrf + "&email=a%40b.co&password=x&passwordConfirm=x&nickname=n");

        assertScreen429(response, "42");
        assertThat(sessionCookie()).contains(session);
        verify(rateLimit, times(1)).recordSignupRequest(any());
    }

    @Test
    void POST_닉네임_제출_제한_초과는_실제_forward되어_429_HTML_안내_페이지를_보인다() throws Exception {
        String csrf = screenCsrf();
        String session = sessionCookie().orElseThrow();

        HttpResponse<String> response = post("/signup/google/nickname", FORM, "_csrf=" + csrf + "&nickname=abc");

        assertScreen429(response, "42");
        assertThat(sessionCookie()).contains(session);
    }

    @Test
    void GET_Google_시작_제한_초과는_기존처럼_429_안내_페이지를_보인다() throws Exception {
        HttpResponse<String> response = get("/oauth2/authorization/google");

        assertScreen429(response, "77");
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void API_회원가입_제한_초과는_기존_JSON_429를_유지한다() throws Exception {
        get("/api/auth/me");
        String xsrf = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst().orElseThrow();

        HttpResponse<String> response = post("/api/auth/signup", "application/json",
                "{\"email\":\"a@b.co\",\"password\":\"Abcdef1!\",\"passwordConfirm\":\"Abcdef1!\",\"nickname\":\"닉네임\"}",
                "X-XSRF-TOKEN", xsrf);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).contains("42");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(response.body()).contains("\"RATE_LIMITED\"");
        assertThat(response.body()).doesNotContain("<!DOCTYPE html>");
    }

    @Test
    void 직접_rate_limited에_접근하면_대기_정보가_없으므로_홈으로_보낸다() throws Exception {
        HttpResponse<String> response = get("/rate-limited");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/");
    }

    @Configuration
    static class ClientIpConfig {

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }

    // @SpringBootConfiguration을 쓰지 않는다. 같은 패키지의 다른 @SpringBootTest가 이 설정을 기본 설정으로 잘못 찾기 때문이다.
    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtConfig.class, TimeConfig.class,
            RefreshTokenConfig.class, AuthCookies.class, GoogleOAuth2Config.class,
            AuthController.class, ScreenAuthController.class, ScreenLogoutController.class,
            GoogleSignupController.class, MyPageController.class, GoogleAccountLinkController.class,
            ScreenRateLimitAdvice.class, RateLimitedPageController.class, ScreenAuthAdvice.class,
            HomeController.class, RestaurantScreenController.class, ClientIpConfig.class})
    static class ForwardTestApp {
    }
}
