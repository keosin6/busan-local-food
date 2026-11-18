package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 화면 회원가입·로그인 (API_SPEC 2.9, 2.10, 10). 실패는 JSON이 아니라 리다이렉트로 응답한다.
 * 판정과 발급은 API 인증 흐름(AuthApiService)을 그대로 쓰고, 이 컨트롤러는 화면 세션과 쿠키를 붙인다.
 * 회원가입 요청 제한은 SignupRateLimitFilter가 집계하므로 여기서 다시 집계하지 않는다.
 * 429는 GlobalExceptionHandler가 만든다(Retry-After 포함). 쿠키와 세션은 건드리지 않는다.
 */
@Controller
public class ScreenAuthController {

    private final AuthApiService authService;
    private final AuthCookies cookies;
    private final ClientIpResolver clientIpResolver;

    public ScreenAuthController(AuthApiService authService, AuthCookies cookies, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.cookies = cookies;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "error", required = false) String error, Model model) {
        model.addAttribute("error", error);
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage(@RequestParam(name = "error", required = false) String error, Model model) {
        model.addAttribute("error", error);
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam(name = "email", defaultValue = "") String email,
                         @RequestParam(name = "password", defaultValue = "") String password,
                         @RequestParam(name = "passwordConfirm", defaultValue = "") String passwordConfirm,
                         @RequestParam(name = "nickname", defaultValue = "") String nickname,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        try {
            IssuedSession session = authService.signup(email, password, passwordConfirm, nickname);
            return signedIn(session, request, response);
        } catch (BusinessException e) {
            return "redirect:/signup?error=" + signupError(e.getErrorCode());
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam(name = "email", defaultValue = "") String email,
                        @RequestParam(name = "password", defaultValue = "") String password,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        String clientIp = clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        try {
            IssuedSession session = authService.login(email, password, clientIp);
            return signedIn(session, request, response);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) {
                return "redirect:/login?error=invalid_credentials";
            }
            if (e.getErrorCode().getStatus() == HttpStatus.BAD_REQUEST) {
                return "redirect:/login?error=validation_failed";
            }
            throw e;
        }
    }

    /** 화면 세션을 만들고 API JWT 쿠키를 함께 내려준 뒤 홈으로 보낸다. */
    private String signedIn(IssuedSession session, HttpServletRequest request, HttpServletResponse response) {
        Member member = session.member();
        ScreenSession.establish(member, request, response);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.accessToken(session.tokens().access()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.refreshToken(session.tokens().refresh()).toString());
        return "redirect:/";
    }

    private static String signupError(ErrorCode code) {
        return switch (code) {
            case EMAIL_DUPLICATED -> "email_duplicated";
            case NICKNAME_DUPLICATED -> "nickname_duplicated";
            default -> {
                if (code.getStatus() == HttpStatus.BAD_REQUEST) {
                    yield "validation_failed";
                }
                throw new BusinessException(code);
            }
        };
    }
}
