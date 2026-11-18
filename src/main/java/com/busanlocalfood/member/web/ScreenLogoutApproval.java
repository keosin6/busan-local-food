package com.busanlocalfood.member.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * 화면 로그아웃 승인 (API_SPEC 2.11, T12 서버 측 강제).
 *
 * API 로그아웃(POST /api/auth/logout)이 성공한 요청에 기존 화면 세션이 있으면, 그 HttpSession에만 일회성 승인을 기록한다.
 * 화면 로그아웃(POST /logout)은 같은 세션의 승인을 소비한 뒤에만 세션을 무효화한다.
 * 승인은 세션 속성이므로 다른 세션, 다른 브라우저, 다른 사용자에게 공유되지 않는다.
 * API 요청은 이 속성을 쓰기만 하고, 인증 수단으로 쓰지 않는다. API 체인은 여전히 STATELESS다.
 */
public record ScreenLogoutApproval(Instant expiresAt) implements Serializable {

    /** 세션 속성 이름. 화면 세션 내부 용도이며 다른 곳에서 읽지 않는다. */
    public static final String SESSION_KEY = "screen.logout.approval";

    /** 승인 유효 기간. API 로그아웃 뒤 화면 로그아웃까지의 시간이다. 재시도는 이 기간 안에 해야 한다. */
    public static final Duration TTL = Duration.ofSeconds(120);

    private static final long serialVersionUID = 1L;

    /**
     * API 로그아웃 성공 뒤에만 호출한다. 기존 세션이 없으면 아무 일도 하지 않는다.
     * 세션을 새로 만들지 않는다(getSession(false)).
     */
    public static void grant(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_KEY, new ScreenLogoutApproval(Instant.now().plus(TTL)));
        }
    }

    /**
     * 화면 로그아웃 전에 호출한다. 승인이 있고 유효하면 true를 반환한다.
     * 승인 속성은 결과와 관계없이 제거한다. 승인은 한 번만 쓸 수 있다.
     */
    public static boolean consume(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        return value instanceof ScreenLogoutApproval approval && Instant.now().isBefore(approval.expiresAt());
    }
}
