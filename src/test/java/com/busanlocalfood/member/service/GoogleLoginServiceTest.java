package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.GoogleLoginService.GoogleLoginDecision;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Google 콜백 판정. 자동 연결과 미검증 거부가 핵심이다. */
class GoogleLoginServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final GoogleLoginService service = new GoogleLoginService(members);

    @Test
    void sub가_연결된_회원이면_로그인이다() {
        Member member = Member.create("user@example.com", null, "구글회원", "sub-1");
        when(members.findByGoogleSub("sub-1")).thenReturn(Optional.of(member));

        assertThat(service.decide("sub-1", "user@example.com", true)).isInstanceOf(GoogleLoginDecision.Login.class);
    }

    @Test
    void 같은_이메일의_기존_회원은_자동_연결하지_않고_거부한다() {
        when(members.findByGoogleSub("sub-2")).thenReturn(Optional.empty());
        when(members.existsByEmail("user@example.com")).thenReturn(true);

        assertThat(service.decide("sub-2", "user@example.com", true))
                .isInstanceOf(GoogleLoginDecision.EmailAccountExists.class);
    }

    @Test
    void 이메일이_미검증이면_연결된_회원이어도_거부한다() {
        Member member = Member.create("user@example.com", null, "구글회원", "sub-1");
        when(members.findByGoogleSub("sub-1")).thenReturn(Optional.of(member));

        assertThat(service.decide("sub-1", "user@example.com", false))
                .isInstanceOf(GoogleLoginDecision.Unverified.class);
        verify(members, never()).findByGoogleSub(anyString());
    }

    @Test
    void 아무도_없으면_신규_가입_대상이고_회원은_만들지_않는다() {
        when(members.findByGoogleSub("sub-3")).thenReturn(Optional.empty());
        when(members.existsByEmail("new@example.com")).thenReturn(false);

        GoogleLoginDecision decision = service.decide("sub-3", "  New@Example.com ", true);

        assertThat(decision).isEqualTo(new GoogleLoginDecision.NewMember("sub-3", "new@example.com"));
    }

    @Test
    void sub나_이메일이_없거나_형식이_틀리면_잘못된_값이다() {
        assertThat(service.decide(" ", "user@example.com", true)).isInstanceOf(GoogleLoginDecision.Invalid.class);
        assertThat(service.decide("sub-1", null, true)).isInstanceOf(GoogleLoginDecision.Invalid.class);
        assertThat(service.decide("sub-1", "not-an-email", true)).isInstanceOf(GoogleLoginDecision.Invalid.class);
    }
}
