package com.busanlocalfood.member.ratelimit;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;

/**
 * 화면 요청의 요청 제한 초과 안내 (API_SPEC 1.4, 10). 필터 단계의 429를 화면 안내 페이지로 넘긴다.
 *
 * /api/** 요청은 기존 JSON 429를 그대로 쓴다. 그 밖의 화면 요청은 /rate-limited로 전달해 HTML 안내를 보여준다.
 * 쿠키와 세션은 건드리지 않는다. 재로그인이나 자동 리다이렉트를 하지 않는다.
 */
public final class ScreenRateLimitPage {

    /** 안내 페이지 경로. 화면 체인에서 공개 경로로 열려 있다. */
    public static final String PATH = "/rate-limited";

    /** 전달 시 남기는 요청 속성. 값은 재시도까지 남은 초(long)다. */
    public static final String RETRY_AFTER_ATTRIBUTE = ScreenRateLimitPage.class.getName() + ".RETRY_AFTER";

    private static final String API_PREFIX = "/api/";

    private ScreenRateLimitPage() {
    }

    /** 화면 요청인지 판정한다. 컨텍스트 경로를 뺀 경로가 /api/로 시작하지 않으면 화면이다. */
    public static boolean isScreenRequest(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        return !path.startsWith(API_PREFIX);
    }

    /** 안내 문구에 쓰는 분 단위 대기 시간. 올림하고 최소 1분이다. */
    public static long retryMinutes(long retryAfterSeconds) {
        return Math.max(1, (retryAfterSeconds + 59) / 60);
    }

    /** 429 상태와 Retry-After를 설정하고 안내 페이지로 전달한다. 전달 뒤 필터 체인은 진행하지 않는다. */
    public static void forward(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws ServletException, IOException {
        request.setAttribute(RETRY_AFTER_ATTRIBUTE, retryAfterSeconds);
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        request.getRequestDispatcher(PATH).forward(request, response);
    }
}
