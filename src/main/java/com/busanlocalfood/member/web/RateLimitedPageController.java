package com.busanlocalfood.member.web;

import com.busanlocalfood.member.ratelimit.ScreenRateLimitPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 화면용 요청 제한 안내 페이지 /rate-limited (API_SPEC 1.4). 필터가 forward로 넘긴 요청만 안내를 보여준다.
 *
 * 429는 재로그인 사유가 아니다. 이 페이지는 세션과 쿠키를 건드리지 않고, 자동 리다이렉트나 자동 재시도를 하지 않는다.
 * 직접 열면 전달된 대기 시간이 없으므로 홈으로 보낸다.
 */
@Controller
public class RateLimitedPageController {

    static final String VIEW = "rate-limited";

    @GetMapping(ScreenRateLimitPage.PATH)
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
