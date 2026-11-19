package com.busanlocalfood.member.oauth;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Google 계정 연동 진행 상태 (API_SPEC 2.6, 2.6.1, 5.7.1). 연동 시작 시점의 로그인 회원 ID와 OAuth2 state를 담는다.
 *
 * 연동 콜백은 이 상태가 세션에 있을 때만 연동 흐름으로 처리한다. 만료된 상태는 제거하지 않고 peek로 읽어,
 * 만료된 연동 콜백이 일반 Google 로그인으로 바뀌지 않게 한다. 만료 판정은 호출자가 isExpired로 한다.
 * 토큰이나 프로필 원문은 저장하지 않는다.
 */
public record GoogleAccountLinkPending(long memberId, String state, Instant expiresAt) implements Serializable {

    /** 세션 속성 이름. */
    public static final String SESSION_KEY = "google.account.link.pending";

    /** 연동 콜백까지 허용하는 기간. */
    public static final Duration TTL = Duration.ofMinutes(15);

    private static final long serialVersionUID = 1L;

    public static void store(HttpSession session, long memberId, String state) {
        session.setAttribute(SESSION_KEY, new GoogleAccountLinkPending(memberId, state, Instant.now().plus(TTL)));
    }

    /** 진행 상태가 있으면 만료 여부와 상관없이 반환한다. 없으면 빈 값이다. 세션은 바꾸지 않는다. */
    public static Optional<GoogleAccountLinkPending> peek(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof GoogleAccountLinkPending pending ? Optional.of(pending) : Optional.empty();
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public static void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }
}
