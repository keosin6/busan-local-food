package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberLoginServiceTest {

    private static final String RAW_PASSWORD = "pass1234!";

    @Mock
    private MemberRepository memberRepository;

    private PasswordEncoder passwordEncoder;
    private MemberLoginService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new MemberLoginService(memberRepository, passwordEncoder);
    }

    private Member passwordMember(String rawPassword) {
        return Member.create("user@example.com", passwordEncoder.encode(rawPassword), "부산먹보", null);
    }

    private ErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("BusinessException이 발생하지 않았습니다.");
    }

    @Test
    void 자격_증명이_맞으면_회원을_반환한다() {
        Member member = passwordMember(RAW_PASSWORD);
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));

        Member result = service.authenticate("User@Example.com", RAW_PASSWORD);

        assertThat(result).isSameAs(member);
        verify(memberRepository).findByEmail("user@example.com");
    }

    @Test
    void 로그인은_가입_조합_규칙을_적용하지_않는다() {
        // 가입 규칙(8자 이상, 조합)을 만족하지 않는 기존 비밀번호도 필수 여부만 검사한다.
        Member member = passwordMember("abc");
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));

        assertThat(service.authenticate("user@example.com", "abc")).isSameAs(member);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 이메일이_없으면_EMAIL_REQUIRED(String email) {
        assertThat(codeOf(() -> service.authenticate(email, RAW_PASSWORD))).isEqualTo(ErrorCode.EMAIL_REQUIRED);
        verify(memberRepository, never()).findByEmail(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a@b", "a b@example.com"})
    void 이메일_형식이_틀리면_EMAIL_INVALID(String email) {
        assertThat(codeOf(() -> service.authenticate(email, RAW_PASSWORD))).isEqualTo(ErrorCode.EMAIL_INVALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 비밀번호가_없으면_PASSWORD_REQUIRED(String password) {
        assertThat(codeOf(() -> service.authenticate("user@example.com", password)))
                .isEqualTo(ErrorCode.PASSWORD_REQUIRED);
        verify(memberRepository, never()).findByEmail(anyString());
    }

    @Test
    void 공백만_있는_비밀번호는_필수_오류가_아니라_자격_증명_오류다() {
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(passwordMember(RAW_PASSWORD)));

        assertThat(codeOf(() -> service.authenticate("user@example.com", "   ")))
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 미가입_이메일이면_INVALID_CREDENTIALS다() {
        when(memberRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.authenticate("none@example.com", RAW_PASSWORD)))
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비밀번호가_틀리면_INVALID_CREDENTIALS다() {
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(passwordMember(RAW_PASSWORD)));

        assertThat(codeOf(() -> service.authenticate("user@example.com", "wrong-pass1!")))
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void Google_전용_계정의_비밀번호_로그인은_INVALID_CREDENTIALS다() {
        Member googleOnly = Member.create("g@example.com", null, "구글회원", "google-sub-1");
        when(memberRepository.findByEmail("g@example.com")).thenReturn(Optional.of(googleOnly));

        assertThat(codeOf(() -> service.authenticate("g@example.com", RAW_PASSWORD)))
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 미가입_비밀번호_불일치_Google_전용_세_경우의_오류가_모두_같다() {
        when(memberRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(passwordMember(RAW_PASSWORD)));
        when(memberRepository.findByEmail("g@example.com"))
                .thenReturn(Optional.of(Member.create("g@example.com", null, "구글회원", "google-sub-1")));

        ErrorCode notFound = codeOf(() -> service.authenticate("none@example.com", RAW_PASSWORD));
        ErrorCode wrongPassword = codeOf(() -> service.authenticate("user@example.com", "wrong-pass1!"));
        ErrorCode googleOnly = codeOf(() -> service.authenticate("g@example.com", RAW_PASSWORD));

        assertThat(notFound).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongPassword).isEqualTo(notFound);
        assertThat(googleOnly).isEqualTo(notFound);
    }

    @Test
    void 실패_시_회원_정보나_해시를_예외_메시지에_노출하지_않는다() {
        Member member = passwordMember(RAW_PASSWORD);
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong-pass1!"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain(member.getPasswordHash())
                        .doesNotContain("wrong-pass1!")
                        .doesNotContain("user@example.com"));
    }
}
