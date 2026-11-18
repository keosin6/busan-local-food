package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class GoogleSignupPendingTest {

    @Test
    void 저장한_진행_상태는_유효한_동안_조회된다() {
        MockHttpSession session = new MockHttpSession();
        GoogleSignupPending.store(session, "sub-1", "user@example.com");

        var current = GoogleSignupPending.current(session);

        assertThat(current).isPresent();
        assertThat(current.get().googleSub()).isEqualTo("sub-1");
        assertThat(current.get().email()).isEqualTo("user@example.com");
    }

    @Test
    void 만료된_진행_상태는_조회되지_않고_제거된다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleSignupPending.SESSION_KEY,
                new GoogleSignupPending("sub-1", "user@example.com", Instant.now().minusSeconds(1)));

        assertThat(GoogleSignupPending.current(session)).isEmpty();
        assertThat(session.getAttribute(GoogleSignupPending.SESSION_KEY)).isNull();
    }

    @Test
    void 세션이_없거나_상태가_없으면_빈_값이다() {
        assertThat(GoogleSignupPending.current(null)).isEmpty();
        assertThat(GoogleSignupPending.current(new MockHttpSession())).isEmpty();
    }

    @Test
    void clear는_진행_상태를_제거한다() {
        MockHttpSession session = new MockHttpSession();
        GoogleSignupPending.store(session, "sub-1", "user@example.com");

        GoogleSignupPending.clear(session);

        assertThat(GoogleSignupPending.current(session)).isEmpty();
    }
}
