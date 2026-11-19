package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.RateLimitExceededException;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.oauth.GoogleAccountLinkPending;
import com.busanlocalfood.member.oauth.GoogleOAuth2Config;
import com.busanlocalfood.member.oauth.GoogleOAuth2Wiring;
import com.busanlocalfood.member.oauth.GoogleOAuth2SuccessHandler;
import com.busanlocalfood.member.oauth.GoogleSignupPending;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.service.AuthApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Google 계정 연동 시작 POST /mypage/google/link (API_SPEC 2.6, 5.7.1).
 *
 * 화면 세션의 로그인 회원만 시작할 수 있다. CSRF는 화면 체인이 검증한다.
 * 이 POST는 Google OAuth2 요청 제한에 1회 집계한다. 이어지는 Google 302는 다시 세지 않는다.
 * 제한을 넘으면 OAuth2 임시 상태를 만들지 않고, 화면 429 안내를 보낸다. 세션과 JWT는 그대로다.
 *
 * Spring의 GET 시작 경로(/oauth2/authorization/google)를 거치지 않는다. 그 경로는 일반 로그인 시작으로 집계되기 때문이다.
 * 대신 같은 인가 요청 저장소와 같은 해석기로 Authorization Request를 만들어 state를 저장하고, Google 인증 페이지로 보낸다.
 */
@Controller
public class GoogleAccountLinkController {

    static final String LINK_PATH = "/mypage/google/link";

    private final ObjectProvider<GoogleOAuth2Wiring> googleWiring;
    private final AuthApiService authService;
    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public GoogleAccountLinkController(ObjectProvider<GoogleOAuth2Wiring> googleWiring,
                                       AuthApiService authService,
                                       AuthRateLimitService rateLimitService,
                                       ClientIpResolver clientIpResolver) {
        this.googleWiring = googleWiring;
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping(LINK_PATH)
    public void start(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var memberId = ScreenSession.currentMemberId();
        if (memberId.isEmpty()) {
            response.sendRedirect("/login");
            return;
        }

        // 1회 집계. 초과하면 아무 임시 상태도 만들지 않는다.
        String ip = clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        RateLimitDecision decision = rateLimitService.recordGoogleOAuthRequest(ip);
        if (decision instanceof RateLimitDecision.Limited limited) {
            throw new RateLimitExceededException(limited.retryAfterSeconds());
        }

        GoogleOAuth2Wiring google = googleWiring.getIfAvailable();
        if (google == null || !google.enabled()) {
            response.sendRedirect(GoogleOAuth2SuccessHandler.LINK_FAILED);
            return;
        }

        Member member;
        try {
            member = authService.findMember(memberId.get());
        } catch (BusinessException e) {
            response.sendRedirect("/login");
            return;
        }
        if (member.isGoogleLinked()) {
            response.sendRedirect(GoogleOAuth2SuccessHandler.LINK_ALREADY_LINKED);
            return;
        }

        // 일반 로그인 진행 상태는 제거한다 (API_SPEC 2.6 처리 2).
        GoogleSignupPending.clear(request.getSession(false));

        OAuth2AuthorizationRequest authorization = resolve(google, request);
        if (authorization == null) {
            response.sendRedirect(GoogleOAuth2SuccessHandler.LINK_FAILED);
            return;
        }
        // state와 인가 요청을 기존 HTTP 세션에 저장한다. 세션이 없으면 여기서 만들며, 로그인 세션 ID는 바뀌지 않는다.
        google.authorizationRequests().saveAuthorizationRequest(authorization, request, response);
        GoogleAccountLinkPending.store(request.getSession(true), memberId.get(), authorization.getState());
        response.sendRedirect(authorization.getAuthorizationRequestUri());
    }

    /**
     * Authorization Request를 만든다. 리다이렉트 URI의 동작 이름은 요청 파라미터 action으로 정해지며,
     * 기본값은 authorize다. 로그인 콜백 URI(/login/oauth2/code/google)와 맞추려고 action=login을 준다.
     */
    private static OAuth2AuthorizationRequest resolve(GoogleOAuth2Wiring google, HttpServletRequest request) {
        OAuth2AuthorizationRequestResolver resolver = google.linkRequestResolver();
        return resolver.resolve(new LoginActionRequest(request), GoogleOAuth2Config.REGISTRATION_ID);
    }

    private static final class LoginActionRequest extends HttpServletRequestWrapper {

        LoginActionRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return "action".equals(name) ? "login" : super.getParameter(name);
        }
    }
}
