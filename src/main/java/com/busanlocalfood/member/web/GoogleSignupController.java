package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.oauth.GoogleSignupPending;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.service.GoogleSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Google 신규 가입 닉네임 설정 (API_SPEC 2.5.1, T3 A안). 화면 경로이며 /api/**가 아니다.
 * 가입 진행 상태가 없으면 접근할 수 없다. 회원 생성과 토큰 발급은 GoogleSignupService가 한 트랜잭션에서 한다.
 * 회원 생성이 성공한 뒤에만 화면 세션과 JWT를 발급한다. 닉네임 제출 요청 제한은 SignupRateLimitFilter가 먼저 집계한다.
 */
@Controller
public class GoogleSignupController {

    static final String NICKNAME_PAGE = "/signup/google/nickname";

    private final GoogleSignupService signupService;
    private final AuthCookies cookies;

    public GoogleSignupController(GoogleSignupService signupService, AuthCookies cookies) {
        this.signupService = signupService;
        this.cookies = cookies;
    }

    @GetMapping(NICKNAME_PAGE)
    public String nicknamePage(@RequestParam(name = "error", required = false) String error,
                               HttpServletRequest request,
                               Model model) {
        if (GoogleSignupPending.current(request.getSession(false)).isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("error", error);
        return "signup-google-nickname";
    }

    @PostMapping(NICKNAME_PAGE)
    public String submitNickname(@RequestParam(name = "nickname", defaultValue = "") String nickname,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Optional<GoogleSignupPending> pending = GoogleSignupPending.current(session);
        if (pending.isEmpty()) {
            return "redirect:/login";
        }
        try {
            IssuedSession issued = signupService.complete(pending.get(), nickname);
            // 세션 생성 시 기존 세션(가입 진행 상태 포함)을 버린다.
            ScreenSession.establish(issued.member(), request, response);
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.accessToken(issued.tokens().access()).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.refreshToken(issued.tokens().refresh()).toString());
            return "redirect:/";
        } catch (BusinessException e) {
            ErrorCode code = e.getErrorCode();
            if (code == ErrorCode.NICKNAME_DUPLICATED) {
                // 진행 상태는 남긴다. 다른 닉네임으로 다시 제출할 수 있다.
                return "redirect:" + NICKNAME_PAGE + "?error=nickname_duplicated";
            }
            if (code.getStatus() == HttpStatus.BAD_REQUEST) {
                return "redirect:" + NICKNAME_PAGE + "?error=validation_failed";
            }
            // 그 밖의 실패는 회원을 만들지 않았으므로 진행 상태를 지운다.
            GoogleSignupPending.clear(session);
            if (code == ErrorCode.EMAIL_DUPLICATED) {
                return "redirect:/login?error=email_account_exists";
            }
            return "redirect:/login?error=google_failed";
        }
    }
}
