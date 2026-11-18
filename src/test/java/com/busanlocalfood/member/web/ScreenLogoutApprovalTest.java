package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 화면 로그아웃 승인 속성의 단위 규칙: 부여, 1회 소비, 만료, 세션 분리. */
class ScreenLogoutApprovalTest {

    @Test
    void 승인은_한_번만_소비할_수_있다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ScreenLogoutApproval.SESSION_KEY,
                new ScreenLogoutApproval(Instant.now().plus(ScreenLogoutApproval.TTL)));

        assertThat(ScreenLogoutApproval.consume(session)).isTrue();
        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
        assertThat(session.getAttribute(ScreenLogoutApproval.SESSION_KEY)).isNull();
    }

    @Test
    void 만료된_승인은_소비되지_않고_속성도_제거된다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ScreenLogoutApproval.SESSION_KEY, new ScreenLogoutApproval(Instant.now().minusSeconds(1)));

        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
        assertThat(session.getAttribute(ScreenLogoutApproval.SESSION_KEY)).isNull();
    }

    @Test
    void 승인이_없으면_소비할_수_없다() {
        assertThat(ScreenLogoutApproval.consume(new MockHttpSession())).isFalse();
    }

    @Test
    void 승인의_유효_기간은_120초다() {
        assertThat(ScreenLogoutApproval.TTL.getSeconds()).isEqualTo(120);
    }
}
