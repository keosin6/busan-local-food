package com.busanlocalfood.member.ratelimit;

import com.busanlocalfood.global.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 회원가입 요청 집계 필터 (API_SPEC 1.4, T15).
 *
 * 실행 위치: Spring Security 체인에서 CsrfFilter 바로 뒤. 따라서 CSRF 검증에 실패한 요청은 여기까지 오지 않고
 * 403으로 거부되어 집계되지 않는다.
 * 집계 시점: 본문을 읽거나 파싱하기 전. 잘못된 JSON, 필드 누락, 지원하지 않는 Content-Type도 모두 집계된다.
 * 대상: POST /signup, POST /api/auth/signup 두 경로만 정확히 일치할 때. 다른 요청은 집계하지 않는다.
 * 화면과 API 회원가입은 같은 IP 카운터를 쓴다.
 *
 * 제한 초과는 RateLimitExceededException으로 바꿔 공통 오류 응답(429, Retry-After)으로 보낸다.
 * 요청 제한 판정을 끝내지 못하면(RateLimitUnavailableException 등) 요청을 진행시키지 않는다 (fail-closed).
 * 이 필터는 쿠키를 읽거나 쓰지 않으며, 세션과 JWT를 바꾸지 않는다.
 *
 * 서블릿 필터 빈으로 등록하지 않는다. SecurityConfig가 체인에 직접 추가하므로 서블릿 컨테이너에서 두 번 실행되지 않는다.
 */
public final class SignupRateLimitFilter extends OncePerRequestFilter {

    static final String SCREEN_SIGNUP_PATH = "/signup";
    static final String API_SIGNUP_PATH = "/api/auth/signup";
    /** Google 신규 가입 닉네임 제출. 회원가입과 같은 IP 카운터를 쓴다 (API_SPEC 2.5.1, 1.4). */
    static final String GOOGLE_NICKNAME_PATH = "/signup/google/nickname";
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver exceptionResolver;

    public SignupRateLimitFilter(AuthRateLimitService rateLimitService,
                                 ClientIpResolver clientIpResolver,
                                 HandlerExceptionResolver exceptionResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isSignupRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String ip = clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader(FORWARDED_FOR));
            RateLimitDecision decision = rateLimitService.recordSignupRequest(ip);
            if (decision instanceof RateLimitDecision.Limited limited) {
                throw new RateLimitExceededException(limited.retryAfterSeconds());
            }
        } catch (RuntimeException e) {
            // 화면 요청의 제한 초과는 안내 페이지로 보낸다 (API_SPEC 1.4). 그 밖에는 요청을 진행시키지 않는다.
            if (e instanceof RateLimitExceededException limited && ScreenRateLimitPage.isScreenRequest(request)) {
                ScreenRateLimitPage.forward(request, response, limited.getRetryAfterSeconds());
                return;
            }
            // 처리된 예외는 공통 오류 응답으로 나가고, 처리되지 않은 예외는 그대로 다시 던진다.
            if (exceptionResolver.resolveException(request, response, null, e) == null) {
                throw e;
            }
            return;
        }
        chain.doFilter(request, response);
    }

    /** 정확한 POST 경로만 대상이다. 접두사 일치, 트레일링 슬래시, 대소문자 차이는 대상이 아니다. */
    static boolean isSignupRequest(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SCREEN_SIGNUP_PATH.equals(path) || API_SIGNUP_PATH.equals(path) || GOOGLE_NICKNAME_PATH.equals(path);
    }
}
