package com.busanlocalfood.member.oauth;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleLoginService.GoogleLoginDecision;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.MemberAccountService.LinkOutcome;
import com.busanlocalfood.member.web.AuthCookies;
import com.busanlocalfood.member.web.ScreenSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Google 콜백 성공 처리 (API_SPEC 2.5, 2.6.1). Spring이 OAuth2 인증을 SecurityContext에 넣은 뒤 호출된다.
 *
 * 처리 경로는 세션의 연동 진행 상태로 나눈다 (DEVELOPMENT_RULES 5.7.1 Callback 핸들러 원칙).
 * - 연동 진행 상태가 있으면 계정 연동이다. 기존 로컬 회원의 인증 상태와 세션을 보존하고, Google 인증은 google_sub 연결에만 쓴다.
 *   세션 ID를 바꾸지 않고, 쿠키를 다시 설정하지 않으며, 일반 로그인 성공 처리를 하지 않는다.
 * - 연동 진행 상태가 없으면 일반 로그인이다.
 *   - 기존 연결 회원: 정식 화면 세션과 JWT 쿠키를 만들고 /로 보낸다.
 *   - 신규 가입 대상: 회원을 만들지 않는다. 가입 진행 상태만 세션에 두고 닉네임 화면으로 보낸다.
 *   - 그 밖(이메일 미검증, 같은 이메일 회원 존재, 값 오류): 기존 인증 상태를 복원하고 /login?error=...로 보낸다.
 *
 * 프로필 값과 토큰은 로그에 남기지 않는다.
 */
public final class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    static final String SIGNUP_NICKNAME_URL = "/signup/google/nickname";
    static final String ERROR_EMAIL_ACCOUNT_EXISTS = "/login?error=email_account_exists";
    static final String ERROR_GOOGLE_UNVERIFIED = "/login?error=google_unverified";
    static final String ERROR_GOOGLE_FAILED = "/login?error=google_failed";

    public static final String LINK_LINKED = "/mypage?google=linked";
    public static final String LINK_UNVERIFIED = "/mypage?error=google_unverified";
    public static final String LINK_ALREADY_LINKED = "/mypage?error=google_already_linked";
    public static final String LINK_FAILED = "/mypage?error=google_failed";

    /** Spring이 연동 콜백에서 저장하는 Google 클라이언트 정보를 지우는 데 쓴다. 같은 세션 저장소를 쓴다. */
    private static final HttpSessionOAuth2AuthorizedClientRepository AUTHORIZED_CLIENTS =
            new HttpSessionOAuth2AuthorizedClientRepository();

    private static final int SUB_MAX_LENGTH = 255;

    private final GoogleLoginService googleLoginService;
    private final AuthApiService authService;
    private final AuthCookies cookies;
    private final MemberAccountService accountService;

    public GoogleOAuth2SuccessHandler(GoogleLoginService googleLoginService,
                                      AuthApiService authService,
                                      AuthCookies cookies,
                                      MemberAccountService accountService) {
        this.googleLoginService = googleLoginService;
        this.authService = authService;
        this.cookies = cookies;
        this.accountService = accountService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Optional<GoogleAccountLinkPending> link = GoogleAccountLinkPending.peek(request.getSession(false));
        if (link.isPresent()) {
            completeLink(link.get(), request, response, authentication);
            return;
        }

        Map<String, Object> attributes = profileAttributes(authentication);
        GoogleLoginDecision decision = attributes == null
                ? new GoogleLoginDecision.Invalid()
                : googleLoginService.decide(
                        asString(attributes.get("sub")),
                        asString(attributes.get("email")),
                        isVerified(attributes.get("email_verified")));

        if (decision instanceof GoogleLoginDecision.Login login) {
            IssuedSession session = authService.issueSession(login.member());
            ScreenSession.establish(login.member(), request, response);
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.accessToken(session.tokens().access()).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.refreshToken(session.tokens().refresh()).toString());
            response.sendRedirect("/");
            return;
        }

        // 정식 로그인이 아니면 Spring이 넣은 OAuth2 인증을 지우고 기존 인증 상태를 되돌린다.
        GoogleOAuth2AuthenticationRestore.restorePrevious(request, response);
        if (decision instanceof GoogleLoginDecision.NewMember newMember) {
            // 가입 진행 상태만 저장한다. 세션과 JWT는 닉네임 설정이 끝난 뒤에만 발급한다.
            GoogleSignupPending.store(request.getSession(true), newMember.googleSub(), newMember.email());
            response.sendRedirect(SIGNUP_NICKNAME_URL);
        } else if (decision instanceof GoogleLoginDecision.EmailAccountExists) {
            response.sendRedirect(ERROR_EMAIL_ACCOUNT_EXISTS);
        } else if (decision instanceof GoogleLoginDecision.Unverified) {
            response.sendRedirect(ERROR_GOOGLE_UNVERIFIED);
        } else {
            response.sendRedirect(ERROR_GOOGLE_FAILED);
        }
    }

    /**
     * 계정 연동 콜백 (API_SPEC 2.6.1). 성공이든 실패든 연동 진행 상태를 지우고, 콜백 전 인증 상태로 되돌린다.
     * 세션은 무효화하지 않고 새로 만들지 않는다. JWT 쿠키도 건드리지 않는다.
     */
    private void completeLink(GoogleAccountLinkPending pending, HttpServletRequest request,
                              HttpServletResponse response, Authentication authentication) throws IOException {
        String target = linkTarget(pending, request, authentication);
        GoogleAccountLinkPending.clear(request.getSession(false));
        removeGoogleAuthorizedClient(authentication, request, response);
        GoogleOAuth2AuthenticationRestore.restorePrevious(request, response);
        response.sendRedirect(target);
    }

    /** 연동 결과의 리다이렉트 경로를 정한다. 판정 순서는 API_SPEC 2.6.1을 따른다. */
    private String linkTarget(GoogleAccountLinkPending pending, HttpServletRequest request,
                              Authentication authentication) {
        if (pending.isExpired(Instant.now())) {
            return LINK_FAILED;
        }
        if (!pending.state().equals(request.getParameter("state"))) {
            return LINK_FAILED;
        }
        // 연동 대상은 연동 시작 시점의 로그인 회원이다. 콜백 직전의 인증 주체가 같은 회원이어야 한다.
        if (!isPreviousMember(request, pending.memberId())) {
            return LINK_FAILED;
        }
        Map<String, Object> attributes = profileAttributes(authentication);
        String sub = attributes == null ? null : asString(attributes.get("sub"));
        if (sub == null || sub.isBlank() || sub.trim().length() > SUB_MAX_LENGTH) {
            return LINK_FAILED;
        }
        if (attributes == null || !isVerified(attributes.get("email_verified"))) {
            return LINK_UNVERIFIED;
        }
        try {
            LinkOutcome outcome = accountService.linkGoogle(pending.memberId(), sub.trim());
            return switch (outcome) {
                case LINKED -> LINK_LINKED;
                case ALREADY_LINKED_TO_OTHER, ALREADY_LINKED -> LINK_ALREADY_LINKED;
                case MEMBER_NOT_FOUND -> LINK_FAILED;
            };
        } catch (BusinessException e) {
            // 동시 요청으로 uk_member_google_sub가 깨진 경우다.
            return e.getErrorCode() == ErrorCode.GOOGLE_ALREADY_LINKED ? LINK_ALREADY_LINKED : LINK_FAILED;
        }
    }

    /** 콜백 전 인증 주체가 해당 회원인지 확인한다. 요청 제한 필터가 기록한 값을 쓴다. */
    private static boolean isPreviousMember(HttpServletRequest request, long memberId) {
        Object previous = request.getAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION);
        return previous instanceof Authentication authentication
                && authentication.getPrincipal() instanceof Long id
                && id == memberId;
    }

    private static void removeGoogleAuthorizedClient(Authentication authentication,
                                                     HttpServletRequest request, HttpServletResponse response) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            AUTHORIZED_CLIENTS.removeAuthorizedClient(
                    token.getAuthorizedClientRegistrationId(), authentication, request, response);
        }
    }

    private static Map<String, Object> profileAttributes(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OAuth2User user) {
            return user.getAttributes();
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    /** Google은 boolean으로 보낸다. 문자열 "true"도 참으로 본다. 그 밖의 값은 미검증이다. */
    private static boolean isVerified(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof String text && "true".equalsIgnoreCase(text));
    }
}
