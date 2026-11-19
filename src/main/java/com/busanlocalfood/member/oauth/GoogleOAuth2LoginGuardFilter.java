package com.busanlocalfood.member.oauth;

import com.busanlocalfood.global.exception.RateLimitExceededException;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.ratelimit.ScreenRateLimitPage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Google OAuth2 요청 제한 (API_SPEC 1.4, R5). 로그인 시작과 콜백이 IP별 15분 10회 카운터를 공유한다.
 *
 * Spring의 OAuth2 시작 필터와 콜백 필터보다 먼저 실행된다. 초과하면 Google 인증 코드 교환 같은 후속 처리를 하지 않는다.
 * 콜백이 초과되면 이 요청의 OAuth2 임시 상태(인가 요청)만 지운다. 기존 로그인 세션과 JWT는 건드리지 않는다.
 * 콜백에서는 처리 전에 현재 인증 상태를 요청 속성에 기록해 두어, 성공·실패 처리에서 기존 로그인 상태를 복원할 수 있게 한다.
 * 한 사용자 요청은 한 번만 집계한다. Google 페이지로의 리다이렉트는 집계하지 않는다.
 */
public final class GoogleOAuth2LoginGuardFilter extends OncePerRequestFilter {

    static final String START_PATH = "/oauth2/authorization/google";
    static final String CALLBACK_PATH = "/login/oauth2/code/google";
    /** 콜백 처리 전 현재 인증 상태. 성공·실패 핸들러가 정식 로그인이 아닐 때 복원한다. */
    public static final String PREVIOUS_AUTHENTICATION = GoogleOAuth2LoginGuardFilter.class.getName() + ".PREVIOUS";
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver exceptionResolver;
    private final HttpSessionOAuth2AuthorizationRequestRepository authorizationRequests;

    public GoogleOAuth2LoginGuardFilter(AuthRateLimitService rateLimitService,
                                        ClientIpResolver clientIpResolver,
                                        HandlerExceptionResolver exceptionResolver,
                                        HttpSessionOAuth2AuthorizationRequestRepository authorizationRequests) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.exceptionResolver = exceptionResolver;
        this.authorizationRequests = authorizationRequests;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean start = isGet(request, START_PATH);
        boolean callback = isGet(request, CALLBACK_PATH);
        if (!start && !callback) {
            chain.doFilter(request, response);
            return;
        }
        if (callback) {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            request.setAttribute(PREVIOUS_AUTHENTICATION, current);
        }
        try {
            String ip = clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader(FORWARDED_FOR));
            RateLimitDecision decision = rateLimitService.recordGoogleOAuthRequest(ip);
            if (decision instanceof RateLimitDecision.Limited limited) {
                throw new RateLimitExceededException(limited.retryAfterSeconds());
            }
        } catch (RuntimeException e) {
            // 후속 OAuth2 처리를 진행시키지 않는다. 콜백이라면 이 요청의 임시 상태(인가 요청, 연동 진행 상태)만 지운다.
            // 기존 로그인 상태, 세션 ID, JWT는 그대로다.
            if (callback) {
                authorizationRequests.removeAuthorizationRequest(request, response);
                GoogleAccountLinkPending.clear(request.getSession(false));
            }
            if (e instanceof RateLimitExceededException limited && ScreenRateLimitPage.isScreenRequest(request)) {
                ScreenRateLimitPage.forward(request, response, limited.getRetryAfterSeconds());
                return;
            }
            if (exceptionResolver.resolveException(request, response, null, e) == null) {
                throw e;
            }
            return;
        }
        if (start) {
            // 일반 로그인 시작은 남아 있던 연동 진행 상태를 지운다 (DEVELOPMENT_RULES 5.7.1 원칙 8).
            // 그렇지 않으면 이후의 일반 콜백이 연동 흐름으로 잘못 분기된다.
            GoogleAccountLinkPending.clear(request.getSession(false));
        }
        chain.doFilter(request, response);
    }

    private static boolean isGet(HttpServletRequest request, String path) {
        return "GET".equals(request.getMethod()) && path.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }
}
