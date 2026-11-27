package com.busanlocalfood.global.config;

import com.busanlocalfood.member.jwt.AccessTokenAuthenticationFilter;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.oauth.GoogleOAuth2Wiring;
import com.busanlocalfood.member.ratelimit.SignupRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 보안 체인 분리 (PR 구현 기준).
 *
 * 1) API 체인 (/api/**): JWT 인증만 쓴다. 세션을 인증에 쓰지 않고 만들지 않는다 (STATELESS, Null 컨텍스트 저장소).
 *    예외: API 로그아웃은 기존 화면 세션이 있으면 화면 로그아웃 승인만 기록한다 (ScreenLogoutApproval). 인증이 아니다.
 *    CSRF는 켜 두고, 토큰은 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더로 주고받는다 (API_SPEC 1.1).
 *    공개 경로는 API_SPEC 2.1~2.4의 인증 경로 네 개만 명시한다. 그 밖의 /api/** 는 인증을 요구한다.
 * 2) 화면 체인 (그 밖의 모든 요청): 기존 세션 인증을 그대로 쓴다. JWT 필터를 넣지 않는다.
 *
 * 회원가입 집계 필터(SignupRateLimitFilter)는 두 체인 모두에 둔다. CSRF 검증 뒤, 본문 읽기 이전에 실행된다.
 * 공개 조회 API(예: GET /api/categories)는 아직 구현되지 않았으므로 여기서 열지 않는다. 구현 시 경로를 명시해서 추가한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    /** API 체인에서 인증 없이 POST할 수 있는 경로. API_SPEC 2.1 회원가입, 2.2 로그인, 2.3 재발급, 2.4 로그아웃. */
    static final String[] PUBLIC_API_AUTH_POST_PATHS = {
            "/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"};

    /** API 체인에서 인증 없이 GET할 수 있는 경로. 카테고리 목록(API_SPEC 3.1)과 맛집 목록·상세(4.1, 4.2)만 연다. */
    static final String[] PUBLIC_API_GET_PATHS = {"/api/categories", "/api/restaurants", "/api/restaurants/*"};

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      Environment environment,
                                                      AccessTokenVerifier accessTokenVerifier,
                                                      AuthRateLimitService rateLimitService,
                                                      ClientIpResolver clientIpResolver,
                                                      @Qualifier("handlerExceptionResolver")
                                                      HandlerExceptionResolver exceptionResolver,
                                                      ObjectMapper objectMapper) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_API_AUTH_POST_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_API_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                // API 인증은 세션을 쓰지 않는다. 화면 세션이 있어도 API 요청의 인증 근거가 되지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                // CSRF: 상태 변경 요청(POST, PUT, PATCH, DELETE)은 검증하되, Authorization 스킴이 Bearer인 요청만 면제한다 (API_SPEC 1.0).
                // Bearer 토큰은 쿠키로 자동 전송되지 않으므로 CSRF 대상이 아니다. Basic 등 다른 스킴과 쿠키 인증, 익명 요청은 검증한다.
                // 면제 판단은 스킴만 보고 토큰 값은 보지 않는다. 잘못된 Bearer는 면제 후 JWT 인증 단계에서 401이 된다.
                // CSRF 필터는 JWT 필터보다 먼저 실행되므로 인증 결과에 의존하지 않는다.
                .csrf(csrf -> csrf
                        .requireCsrfProtectionMatcher(SecurityConfig::requiresCsrfForApi)
                        .csrfTokenRepository(apiCsrfTokenRepository(environment))
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new ApiAuthenticationEntryPoint(objectMapper)))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new SignupRateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver),
                        CsrfFilter.class)
                .addFilterBefore(new AccessTokenAuthenticationFilter(accessTokenVerifier), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * API 체인의 CSRF 검사 대상 (API_SPEC 1.0).
     * - 안전 메서드(GET, HEAD, TRACE, OPTIONS): 검사하지 않는다.
     * - Bearer 스킴의 Authorization 헤더: 검사하지 않는다. 토큰 값이 틀려도 면제하며, 인증 단계에서 401이 된다.
     * - 그 밖(Basic 등 다른 스킴, Authorization 헤더 없음, 익명): 검사한다.
     * 쿠키 인증은 Authorization 헤더가 없을 때만 일어나므로, 이 면제가 쿠키 인증 요청을 빠뜨리지 않는다.
     */
    static boolean requiresCsrfForApi(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        return !AccessTokenAuthenticationFilter.hasBearerScheme(request);
    }

    /**
     * XSRF-TOKEN 쿠키 (API_SPEC 1.1): HttpOnly=false, SameSite=Lax, Path=/. 운영 프로필에서만 Secure=true.
     * 쿠키는 JS가 읽어 X-XSRF-TOKEN 헤더에 실어야 하므로 HttpOnly를 끈다.
     */
    static CookieCsrfTokenRepository apiCsrfTokenRepository(Environment environment) {
        boolean secure = environment.acceptsProfiles(Profiles.of("prod"));
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secure));
        return repository;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain screenSecurityFilterChain(HttpSecurity http,
                                                         AuthRateLimitService rateLimitService,
                                                         ClientIpResolver clientIpResolver,
                                                         @Qualifier("handlerExceptionResolver")
                                                         HandlerExceptionResolver exceptionResolver,
                                                         ObjectProvider<GoogleOAuth2Wiring> googleWiring) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // 보호 화면(API_SPEC 10): 마이페이지와 계정 관리, 맛집 등록·수정 화면.
                // 화면 세션이 없으면 /login으로 보낸다. JWT 쿠키만으로는 화면 인증되지 않는다.
                // 맛집 수정의 작성자 본인 확인은 F1-02에서 서버 측 서비스가 맡는다. 여기서는 로그인 여부만 본다.
                .requestMatchers("/mypage", "/mypage/**", "/restaurants/new", "/restaurants/*/edit",
                        "/restaurants/*/delete").authenticated()
                .anyRequest().permitAll());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));

        // 회원가입 요청 집계 (API_SPEC 1.4). CSRF 검증 뒤, 본문 읽기 이전에 실행한다. CSRF 실패 요청은 집계되지 않는다.
        http.addFilterAfter(new SignupRateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver),
                CsrfFilter.class);

        // Google OAuth2 로그인 (API_SPEC 2.5). 화면 체인에만 적용한다. 설정(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)이 있을 때만 구성한다.
        // 요청 제한 필터는 Spring의 시작·콜백 필터보다 먼저 실행한다. state 검증과 인가 요청 저장은 Spring 기본 세션 방식을 쓴다.
        // 성공·실패 핸들러는 정식 로그인이 아닌 경우 기존 인증 상태를 복원한다. 화면 세션은 기존 ScreenSession으로만 만든다.
        GoogleOAuth2Wiring google = googleWiring.getIfAvailable();
        if (google != null && google.enabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestRepository(google.authorizationRequests()))
                    .successHandler(google.success())
                    .failureHandler(google.failure()));
            http.addFilterBefore(google.guard(), OAuth2AuthorizationRequestRedirectFilter.class);
            // 계정 연동 콜백은 기존 세션의 세션 ID를 바꾸지 않는다. 일반 로그인 콜백은 기존 전략을 그대로 쓴다.
            http.sessionManagement(session -> session.sessionAuthenticationStrategy(google.sessionStrategy()));
        }

        // 화면 로그아웃 POST /logout은 ScreenLogoutController가 처리한다 (API_SPEC 2.11, T12).
        // 기본 LogoutFilter는 쓰지 않는다. 필터는 승인 검증 없이 세션을 끊기 때문이다.
        // 같은 세션에서 API 로그아웃이 성공했을 때만 승인이 남고, 컨트롤러가 승인을 소비한 뒤에만 세션을 무효화한다.
        http.logout(AbstractHttpConfigurer::disable);

        // TODO(T11): OAuth2 임시 세션 정책은 Google OAuth2 단계에서 확정한다. 지금은 화면 로그인의 세션 고정 방어만 적용한다
        //  (ScreenSession). 세션 정책을 완전 Stateless로 고정하지 않는다 (DEVELOPMENT_RULES 5.7).
        // CSRF 보호는 기본값(활성)을 유지한다. 쿠키 기반 인증을 사용하므로 비활성화하지 않는다.
        return http.build();
    }
}
