package com.busanlocalfood.member.oauth;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Google 신규 가입 진행 상태 (API_SPEC 2.5, 2.5.1). OAuth2 임시 세션의 별도 속성이다.
 * 정식 인증이 아니다. 이 상태만으로는 화면 세션과 JWT를 발급하지 않는다.
 * 저장하는 값은 Google sub, 검증된 이메일, 만료 시각뿐이다. 토큰과 프로필 원문은 저장하지 않는다.
 */
public record GoogleSignupPending(String googleSub, String email, Instant expiresAt) implements Serializable {

    /** 세션 속성 이름. */
    public static final String SESSION_KEY = "google.signup.pending";

    /** 닉네임 설정까지 허용하는 기간. */
    public static final Duration TTL = Duration.ofMinutes(15);

    private static final long serialVersionUID = 1L;

    public static void store(HttpSession session, String googleSub, String email) {
        session.setAttribute(SESSION_KEY, new GoogleSignupPending(googleSub, email, Instant.now().plus(TTL)));
    }

    /** 유효한 진행 상태가 있으면 반환한다. 없거나 만료되었으면 속성을 제거하고 빈 값을 반환한다. */
    public static Optional<GoogleSignupPending> current(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof GoogleSignupPending pending && Instant.now().isBefore(pending.expiresAt())) {
            return Optional.of(pending);
        }
        session.removeAttribute(SESSION_KEY);
        return Optional.empty();
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }
}
