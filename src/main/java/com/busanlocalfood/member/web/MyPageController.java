package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.MemberAccountService;
import com.busanlocalfood.member.service.MemberAccountService.PasswordOutcome;
import com.busanlocalfood.member.service.MemberAccountService.UnlinkOutcome;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 마이페이지와 계정 관리 화면 (API_SPEC 2.7, 2.7.1, 10). 화면 세션으로만 접근한다. /mypage/**는 화면 체인이 인증을 요구한다.
 *
 * 비밀번호 원문은 요청 본문으로만 받는다. 리다이렉트 URL, 모델, 로그에 넣지 않는다.
 * 실패 사유는 쿼리 코드로만 보낸다. 세션과 JWT는 이 컨트롤러가 바꾸지 않는다.
 * Google 계정 연동 시작은 GoogleAccountLinkController가 맡는다.
 */
@Controller
public class MyPageController {

    private final AuthApiService authService;
    private final MemberAccountService accountService;

    public MyPageController(AuthApiService authService, MemberAccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @GetMapping("/mypage")
    public String mypage(@RequestParam(name = "error", required = false) String error,
                         @RequestParam(name = "google", required = false) String googleResult,
                         @RequestParam(name = "password", required = false) String passwordResult,
                         Model model) {
        Optional<Member> member = currentMember();
        if (member.isEmpty()) {
            return "redirect:/login";
        }
        Member current = member.get();
        model.addAttribute("email", current.getEmail());
        model.addAttribute("nickname", current.getNickname());
        model.addAttribute("googleLinked", current.isGoogleLinked());
        model.addAttribute("passwordSet", current.hasPassword());
        model.addAttribute("error", error);
        model.addAttribute("googleResult", googleResult);
        model.addAttribute("passwordResult", passwordResult);
        return "mypage";
    }

    /** Google 계정 연동 해제 (API_SPEC 2.7). 본인 세션의 회원만 처리한다. */
    @PostMapping("/mypage/google/unlink")
    public String unlinkGoogle(@RequestParam(name = "password", defaultValue = "") String password) {
        Optional<Long> memberId = ScreenSession.currentMemberId();
        if (memberId.isEmpty()) {
            return "redirect:/login";
        }
        UnlinkOutcome outcome = accountService.unlinkGoogle(memberId.get(), password);
        return switch (outcome) {
            case UNLINKED -> "redirect:/mypage?google=unlinked";
            case NOT_LINKED -> "redirect:/mypage?error=google_not_linked";
            case PASSWORD_REQUIRED -> "redirect:/mypage?error=password_required";
            case INVALID_CREDENTIALS -> "redirect:/mypage?error=invalid_credentials";
            case MEMBER_NOT_FOUND -> "redirect:/login";
        };
    }

    /** 로컬 비밀번호 설정 화면 (API_SPEC 2.7.1). 이미 설정된 회원은 설정 화면을 보지 못한다. */
    @GetMapping("/mypage/password")
    public String passwordPage(@RequestParam(name = "error", required = false) String error, Model model) {
        Optional<Member> member = currentMember();
        if (member.isEmpty()) {
            return "redirect:/login";
        }
        if (member.get().hasPassword()) {
            return "redirect:/mypage?error=password_already_set";
        }
        model.addAttribute("error", error);
        return "mypage-password";
    }

    /** 로컬 비밀번호 설정 제출 (API_SPEC 2.7.1). 비밀번호 변경은 이 경로에서 하지 않는다. */
    @PostMapping("/mypage/password")
    public String setPassword(@RequestParam(name = "password", defaultValue = "") String password,
                              @RequestParam(name = "passwordConfirm", defaultValue = "") String passwordConfirm) {
        Optional<Long> memberId = ScreenSession.currentMemberId();
        if (memberId.isEmpty()) {
            return "redirect:/login";
        }
        PasswordOutcome outcome = accountService.setLocalPassword(memberId.get(), password, passwordConfirm);
        return switch (outcome) {
            case SET -> "redirect:/mypage?password=set";
            case ALREADY_SET -> "redirect:/mypage?error=password_already_set";
            case INVALID -> "redirect:/mypage/password?error=validation_failed";
            case MEMBER_NOT_FOUND -> "redirect:/login";
        };
    }

    /** 현재 화면 세션의 회원. 세션에 회원 ID가 없거나 회원이 DB에 없으면 빈 값이다. */
    private Optional<Member> currentMember() {
        Optional<Long> memberId = ScreenSession.currentMemberId();
        if (memberId.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(authService.findMember(memberId.get()));
        } catch (BusinessException e) {
            return Optional.empty();
        }
    }
}
