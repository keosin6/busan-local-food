package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.MemberAccountService.LinkOutcome;
import com.busanlocalfood.member.service.MemberAccountService.PasswordOutcome;
import com.busanlocalfood.member.service.MemberAccountService.UnlinkOutcome;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 계정 연결·해제·비밀번호 설정의 판정 순서와 저장 결과. DB 없이 저장소를 목으로 바꾼다. */
class MemberAccountServiceTest {

    private static final String PASSWORD = "Abcdef1!";
    private static final String SUB = "google-sub-1";

    private MemberRepository repository;
    private PasswordEncoder encoder;
    private MemberAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(MemberRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new MemberAccountService(repository, encoder);
    }

    private static Member memberWithId(long id, String passwordHash, String googleSub) {
        Member member = passwordHash == null
                ? Member.create("g@example.com", null, "구글회원", googleSub)
                : Member.create("m@example.com", passwordHash, "회원", googleSub);
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    void 연결되지_않은_회원에게_새_sub를_연결하면_LINKED이고_저장한다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(repository.findByGoogleSub(SUB)).thenReturn(Optional.empty());

        assertThat(service.linkGoogle(7L, SUB)).isEqualTo(LinkOutcome.LINKED);
        assertThat(member.getGoogleSub()).isEqualTo(SUB);
        verify(repository).saveAndFlush(member);
    }

    @Test
    void 같은_회원에게_이미_같은_sub가_있으면_변경_없이_LINKED다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(repository.findByGoogleSub(SUB)).thenReturn(Optional.of(member));

        assertThat(service.linkGoogle(7L, SUB)).isEqualTo(LinkOutcome.LINKED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 다른_회원에게_연결된_sub는_ALREADY_LINKED_TO_OTHER이고_저장하지_않는다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), null);
        Member other = memberWithId(8L, encoder.encode(PASSWORD), SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(repository.findByGoogleSub(SUB)).thenReturn(Optional.of(other));

        assertThat(service.linkGoogle(7L, SUB)).isEqualTo(LinkOutcome.ALREADY_LINKED_TO_OTHER);
        assertThat(member.getGoogleSub()).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_다른_sub가_연결된_회원이면_ALREADY_LINKED다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), "old-sub");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(repository.findByGoogleSub(SUB)).thenReturn(Optional.empty());

        assertThat(service.linkGoogle(7L, SUB)).isEqualTo(LinkOutcome.ALREADY_LINKED);
        assertThat(member.getGoogleSub()).isEqualTo("old-sub");
    }

    @Test
    void 회원이_없으면_MEMBER_NOT_FOUND이다() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThat(service.linkGoogle(7L, SUB)).isEqualTo(LinkOutcome.MEMBER_NOT_FOUND);
    }

    @Test
    void UNIQUE_경합으로_uk_member_google_sub가_깨지면_GOOGLE_ALREADY_LINKED로_던진다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(repository.findByGoogleSub(SUB)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate",
                new org.hibernate.exception.ConstraintViolationException("dup", null, "uk_member_google_sub")));

        assertThatThrownBy(() -> service.linkGoogle(7L, SUB))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_ALREADY_LINKED));
    }

    @Test
    void 연결되지_않은_회원의_해제_요청은_NOT_LINKED다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), null);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.unlinkGoogle(7L, PASSWORD)).isEqualTo(UnlinkOutcome.NOT_LINKED);
    }

    @Test
    void Google_전용_회원은_비밀번호가_없어_PASSWORD_REQUIRED이고_해제하지_않는다() {
        Member member = memberWithId(7L, null, SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.unlinkGoogle(7L, PASSWORD)).isEqualTo(UnlinkOutcome.PASSWORD_REQUIRED);
        assertThat(member.getGoogleSub()).isEqualTo(SUB);
    }

    @Test
    void 비밀번호가_틀리거나_비어_있으면_INVALID_CREDENTIALS이고_해제하지_않는다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.unlinkGoogle(7L, "Wrong1!!")).isEqualTo(UnlinkOutcome.INVALID_CREDENTIALS);
        assertThat(service.unlinkGoogle(7L, "")).isEqualTo(UnlinkOutcome.INVALID_CREDENTIALS);
        assertThat(member.getGoogleSub()).isEqualTo(SUB);
    }

    @Test
    void 비밀번호가_맞으면_google_sub를_NULL로_저장하고_UNLINKED다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.unlinkGoogle(7L, PASSWORD)).isEqualTo(UnlinkOutcome.UNLINKED);
        assertThat(member.getGoogleSub()).isNull();
        assertThat(member.hasPassword()).isTrue();
        verify(repository).saveAndFlush(member);
    }

    @Test
    void 이미_비밀번호가_있으면_ALREADY_SET이고_인코더를_호출하지_않는다() {
        Member member = memberWithId(7L, encoder.encode(PASSWORD), SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        PasswordEncoder spy = mock(PasswordEncoder.class);
        MemberAccountService withSpy = new MemberAccountService(repository, spy);

        assertThat(withSpy.setLocalPassword(7L, PASSWORD, PASSWORD)).isEqualTo(PasswordOutcome.ALREADY_SET);
        verify(spy, never()).encode(anyString());
    }

    @Test
    void 정책_위반이나_확인값_불일치는_INVALID이고_저장하지_않는다() {
        Member member = memberWithId(7L, null, SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.setLocalPassword(7L, "", "")).isEqualTo(PasswordOutcome.INVALID);
        assertThat(service.setLocalPassword(7L, "short1!", "short1!")).isEqualTo(PasswordOutcome.INVALID);
        assertThat(service.setLocalPassword(7L, PASSWORD, "Abcdef1@")).isEqualTo(PasswordOutcome.INVALID);
        assertThat(service.setLocalPassword(7L, "Abcdefgh!", "Abcdefgh!")).isEqualTo(PasswordOutcome.INVALID);
        assertThat(member.hasPassword()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void 정상_설정은_BCrypt_해시만_저장하고_원문은_저장하지_않는다() {
        Member member = memberWithId(7L, null, SUB);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        assertThat(service.setLocalPassword(7L, PASSWORD, PASSWORD)).isEqualTo(PasswordOutcome.SET);
        assertThat(member.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(member.getPasswordHash()).startsWith("$2");
        assertThat(encoder.matches(PASSWORD, member.getPasswordHash())).isTrue();
        assertThat(member.getGoogleSub()).isEqualTo(SUB);
    }
}
