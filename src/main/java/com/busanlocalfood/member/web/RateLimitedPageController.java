package com.busanlocalfood.member.web;

import com.busanlocalfood.member.ratelimit.ScreenRateLimitPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 화면용 요청 제한 안내 페이지 /rate-limited (API_SPEC 1.4). 필터가 forward로 넘긴 요청만 안내를 보여준다.
 *
 * 429는 재로그인 사유가 아니다. 이 페이지는 세션과 쿠키를 건드리지 않고, 자동 리다이렉트나 자동 재시도를 하지 않는다.
 * 직접 열면 전달된 대기 시간이 없으므로 홈으로 보낸다.
 *
 * 필터는 원래 요청 메서드를 유지한 채 forward한다. 따라서 POST 제한(회원가입, 닉네임 제출)도 이 페이지로 와야 하므로
 * GET과 POST를 모두 받는다. 제한 정보가 없는 요청만 홈으로 보낸다.
 */
@Controller
public class RateLimitedPageController {

    static final String VIEW = "rate-limited";

    @RequestMapping(value = ScreenRateLimitPage.PATH, method = {RequestMethod.GET, RequestMethod.POST})
    public String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object retryAfter = request.getAttribute(ScreenRateLimitPage.RETRY_AFTER_ATTRIBUTE);
        if (!(retryAfter instanceof Long seconds)) {
            return "redirect:/";
        }
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        model.addAttribute("retryAfterSeconds", seconds);
        model.addAttribute("retryMinutes", ScreenRateLimitPage.retryMinutes(seconds));
        return VIEW;
    }
}
