package com.busanlocalfood.member.oauth;

import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.service.GoogleLoginService.GoogleLoginDecision;
import com.busanlocalfood.member.web.AuthCookies;
import com.busanlocalfood.member.web.ScreenSession;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Google 콜백 성공 처리 (API_SPEC 2.5). Spring이 OAuth2 인증을 SecurityContext에 넣은 뒤 호출된다.
 *
 * - 기존 연결 회원: 정식 화면 세션과 JWT 쿠키를 만들고 /로 보낸다.
 * - 신규 가입 대상: 회원을 만들지 않는다. 가입 진행 상태만 세션에 두고 닉네임 화면으로 보낸다. 정식 인증은 만들지 않는다.
 * - 그 밖(이메일 미검증, 같은 이메일 회원 존재, 값 오류): 기존 인증 상태를 복원하고 /login?error=...로 보낸다.
 *
 * 프로필 값과 토큰은 로그에 남기지 않는다.
 */
public final class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    static final String SIGNUP_NICKNAME_URL = "/signup/google/nickname";
    static final String ERROR_EMAIL_ACCOUNT_EXISTS = "/login?error=email_account_exists";
    static final String ERROR_GOOGLE_UNVERIFIED = "/login?error=google_unverified";
    static final String ERROR_GOOGLE_FAILED = "/login?error=google_failed";

    private final GoogleLoginService googleLoginService;
    private final AuthApiService authService;
    private final AuthCookies cookies;

    public GoogleOAuth2SuccessHandler(GoogleLoginService googleLoginService,
                                      AuthApiService authService,
                                      AuthCookies cookies) {
        this.googleLoginService = googleLoginService;
        this.authService = authService;
        this.cookies = cookies;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
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
