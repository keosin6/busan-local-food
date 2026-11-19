package com.busanlocalfood.member.web;

import com.busanlocalfood.global.exception.RateLimitExceededException;
import com.busanlocalfood.member.ratelimit.ScreenRateLimitPage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * 화면 컨트롤러의 요청 제한 초과를 안내 페이지(429)로 바꾼다 (API_SPEC 1.4).
 *
 * 적용 대상은 화면 컨트롤러로 한정한다. API 컨트롤러의 429 JSON 응답은 GlobalExceptionHandler가 그대로 만든다.
 * 쿠키와 세션은 건드리지 않는다.
 */
@ControllerAdvice(assignableTypes = {
        ScreenAuthController.class,
        GoogleSignupController.class,
        GoogleAccountLinkController.class,
        MyPageController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScreenRateLimitAdvice {

    @ExceptionHandler(RateLimitExceededException.class)
    public ModelAndView handleRateLimited(RateLimitExceededException e, HttpServletResponse response) {
        long seconds = e.getRetryAfterSeconds();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        ModelAndView view = new ModelAndView(RateLimitedPageController.VIEW);
        view.setStatus(HttpStatus.TOO_MANY_REQUESTS);
        view.addObject("retryAfterSeconds", seconds);
        view.addObject("retryMinutes", ScreenRateLimitPage.retryMinutes(seconds));
        // 이 응답은 @ModelAttribute를 거치지 않으므로 헤더 표시용 로그인 상태를 직접 넣는다.
        view.addObject("loggedIn", ScreenSession.currentMemberId().isPresent());
        return view;
    }
}
