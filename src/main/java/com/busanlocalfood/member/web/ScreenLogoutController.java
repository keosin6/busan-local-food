package com.busanlocalfood.member.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 화면 로그아웃 POST /logout (API_SPEC 2.11, T12).
 *
 * 기본 Spring LogoutFilter 대신 이 컨트롤러가 처리한다. 필터는 CSRF 검증 뒤에 실행되므로
 * CSRF가 틀리면 승인도 소비되지 않는다.
 *
 * - 세션이 없으면 끊을 것이 없으므로 /login으로 보낸다 (중복 로그아웃 안전).
 * - 세션은 있는데 승인이 없거나 만료되었거나 이미 소비되었으면 403으로 거부하고 세션을 유지한다.
 * - 승인이 유효하면 승인을 소비하고, SecurityContext를 지우고, 세션을 무효화하고, JSESSIONID를 만료시킨 뒤 /login으로 보낸다.
 *
 * refresh_token과 access_token 쿠키는 읽지도 지우지도 않는다.
 */
@Controller
public class ScreenLogoutController {

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return redirectToLogin(null);
        }
        if (!ScreenLogoutApproval.consume(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SecurityContextHolder.clearContext();
        session.invalidate();
        ResponseCookie expired = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
        return redirectToLogin(expired);
    }

    private static ResponseEntity<Void> redirectToLogin(ResponseCookie expiredSessionCookie) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/login"));
        if (expiredSessionCookie != null) {
            builder.header(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString());
        }
        return builder.build();
    }
}
