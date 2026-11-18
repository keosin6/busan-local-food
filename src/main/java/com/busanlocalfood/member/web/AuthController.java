package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.RateLimitExceededException;
import com.busanlocalfood.global.response.ApiErrorResponse;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.IssuedAccessToken;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.AuthApiService.IssuedTokens;
import com.busanlocalfood.member.service.AuthApiService.Renewal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 인증 HTTP 엔드포인트 (API_SPEC 2.1~2.4, 2.8). 흐름은 AuthApiService가 맡고, 이 클래스는 HTTP와 쿠키만 다룬다.
 * 세션은 만들거나 읽지 않는다. 원문 토큰은 본문에 넣지 않고 쿠키로만 내보낸다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final AuthApiService authService;
    private final AuthCookies cookies;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthApiService authService, AuthCookies cookies, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.cookies = cookies;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiSuccessResponse<SignupResponse>> signup(@RequestBody SignupRequest body) {
        IssuedSession session = authService.signup(body.email(), body.password(), body.passwordConfirm(), body.nickname());
        Member member = session.member();
        SignupResponse data = new SignupResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(tokenHeaders(session.tokens()))
                .body(ApiSuccessResponse.of(data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiSuccessResponse<LoginResponse>> login(@RequestBody LoginRequest body,
                                                                   HttpServletRequest request) {
        String clientIp = clientIp(request);
        IssuedSession session = authService.login(body.email(), body.password(), clientIp);
        Member member = session.member();
        LoginResponse data = new LoginResponse(member.getId(), member.getNickname(), member.getRole(),
                expiresInSeconds(session.tokens().access()));
        return ResponseEntity.ok()
                .headers(tokenHeaders(session.tokens()))
                .body(ApiSuccessResponse.of(data));
    }

    /**
     * Refresh Token 재발급. Access Token 인증은 요구하지 않는다.
     * 429는 쿠키를 건드리지 않고, 401(행 없음, 만료, 폐기, 재사용)은 JWT 쿠키를 만료시킨다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
        Renewal renewal = authService.renew(refreshToken);
        if (renewal instanceof Renewal.Renewed renewed) {
            IssuedTokens tokens = renewed.tokens();
            return ResponseEntity.ok()
                    .headers(tokenHeaders(tokens))
                    .body(ApiSuccessResponse.of(new RefreshResponse(expiresInSeconds(tokens.access()))));
        }
        if (renewal instanceof Renewal.RateLimited limited) {
            throw new RateLimitExceededException(limited.retryAfterSeconds());
        }
        return ResponseEntity.status(ErrorCode.TOKEN_INVALID.getStatus())
                .headers(expiredHeaders())
                .body(ApiErrorResponse.of(ErrorCode.TOKEN_INVALID));
    }

    /** 로그아웃. 인증 없이 호출할 수 있고, 쿠키가 없거나 폐기된 토큰이어도 200이다. */
    @PostMapping("/logout")
    public ResponseEntity<ApiSuccessResponse<Void>> logout(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {
        authService.logout(refreshToken);
        // API 로그아웃이 예외 없이 끝난 뒤에만 기존 화면 세션에 승인을 남긴다 (T12 서버 측 순서 보장, API_SPEC 2.11).
        ScreenLogoutApproval.grant(request);
        return ResponseEntity.ok()
                .headers(expiredHeaders())
                .body(ApiSuccessResponse.of(null));
    }

    @GetMapping("/me")
    public ApiSuccessResponse<MeResponse> me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Long memberId)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Member member = authService.findMember(memberId);
        return ApiSuccessResponse.of(new MeResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getGoogleSub() != null,
                member.getPasswordHash() != null));
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader(FORWARDED_FOR));
    }

    private HttpHeaders tokenHeaders(IssuedTokens tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookies.accessToken(tokens.access()).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookies.refreshToken(tokens.refresh()).toString());
        return headers;
    }

    private HttpHeaders expiredHeaders() {
        HttpHeaders headers = new HttpHeaders();
        for (ResponseCookie cookie : cookies.expireAll()) {
            headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return headers;
    }

    private static long expiresInSeconds(IssuedAccessToken token) {
        return Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
    }

    /** 회원가입 요청 본문. 알 수 없는 필드는 거부한다 (application.yml). */
    public record SignupRequest(String email, String password, String passwordConfirm, String nickname) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record SignupResponse(long memberId, String email, String nickname, Role role) {
    }

    public record LoginResponse(long memberId, String nickname, Role role, long accessTokenExpiresIn) {
    }

    public record RefreshResponse(long accessTokenExpiresIn) {
    }

    public record MeResponse(long memberId, String email, String nickname, Role role,
                             boolean googleLinked, boolean hasPassword) {
    }
}
