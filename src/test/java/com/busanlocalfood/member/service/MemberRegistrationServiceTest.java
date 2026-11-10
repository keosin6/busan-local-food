package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberRegistrationServiceTest {

    private static final String VALID_PASSWORD = "pass1234!";

    @Mock
    private MemberRepository memberRepository;

    private PasswordEncoder passwordEncoder;
    private MemberRegistrationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new MemberRegistrationService(memberRepository, passwordEncoder);
        // 기본값: 중복 없음, 저장은 전달받은 객체를 그대로 반환
        // lenient: 검증 실패 테스트는 이 기본 스텁을 쓰지 않으므로 엄격 모드의 미사용 오류를 피한다.
        lenient().when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(memberRepository.existsByNickname(anyString())).thenReturn(false);
        lenient().when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Member register(String email, String password, String confirm, String nickname) {
        return service.register(email, password, confirm, nickname);
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Member captureSaved() {
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ---- 정상 처리 ----

    @Test
    void 정상_가입이면_USER_회원을_저장하고_반환한다() {
        Member result = register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보");

        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getNickname()).isEqualTo("부산먹보");
        assertThat(result.getRole()).isEqualTo(Role.USER);
        assertThat(result.getGoogleSub()).isNull();
    }

    @Test
    void 이메일은_공백_제거와_소문자_정규화_후_중복_검사하고_저장한다() {
        register("  User@Example.COM  ", VALID_PASSWORD, VALID_PASSWORD, "부산먹보");

        verify(memberRepository).existsByEmail("user@example.com");
        assertThat(captureSaved().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void 닉네임은_앞뒤_공백을_제거하고_저장한다() {
        register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, "  부산먹보  ");

        verify(memberRepository).existsByNickname("부산먹보");
        assertThat(captureSaved().getNickname()).isEqualTo("부산먹보");
    }

    @Test
    void 비밀번호는_BCrypt_해시로만_저장되고_원문과_다르다() {
        register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보");

        Member saved = captureSaved();
        assertThat(saved.getPasswordHash()).isNotEqualTo(VALID_PASSWORD);
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(VALID_PASSWORD, saved.getPasswordHash())).isTrue();
    }

    // ---- 이메일 검증 ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 이메일이_없으면_EMAIL_REQUIRED(String email) {
        assertCode(() -> register(email, VALID_PASSWORD, VALID_PASSWORD, "부산먹보"), ErrorCode.EMAIL_REQUIRED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a@b", "a b@example.com", "@example.com", "user@"})
    void 이메일_형식이_틀리면_EMAIL_INVALID(String email) {
        assertCode(() -> register(email, VALID_PASSWORD, VALID_PASSWORD, "부산먹보"), ErrorCode.EMAIL_INVALID);
    }

    @Test
    void 이메일이_255자를_넘으면_EMAIL_INVALID() {
        String longEmail = "a".repeat(250) + "@x.com"; // 256자
        assertCode(() -> register(longEmail, VALID_PASSWORD, VALID_PASSWORD, "부산먹보"), ErrorCode.EMAIL_INVALID);
    }

    @Test
    void 이메일_중복이면_EMAIL_DUPLICATED이고_저장하지_않는다() {
        when(memberRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertCode(() -> register("dup@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"),
                ErrorCode.EMAIL_DUPLICATED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    // ---- 닉네임 검증 ----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 닉네임이_없으면_NICKNAME_REQUIRED(String nickname) {
        assertCode(() -> register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, nickname),
                ErrorCode.NICKNAME_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "가가가가가가가가가가가가가가가가가가가가가", "부산!", "부 산", "맛집-1", "ㄱㄴ"})
    void 닉네임_길이와_허용_문자를_벗어나면_NICKNAME_INVALID(String nickname) {
        assertCode(() -> register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, nickname),
                ErrorCode.NICKNAME_INVALID);
    }

    @Test
    void 닉네임_중복이면_NICKNAME_DUPLICATED이고_저장하지_않는다() {
        when(memberRepository.existsByNickname("부산먹보")).thenReturn(true);

        assertCode(() -> register("user@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"),
                ErrorCode.NICKNAME_DUPLICATED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이메일과_닉네임이_동시에_중복이면_이메일_오류를_우선한다() {
        when(memberRepository.existsByEmail("dup@example.com")).thenReturn(true);
        // 이메일 오류가 먼저 반환되므로 닉네임 검사 스텁은 호출되지 않을 수 있다.
        lenient().when(memberRepository.existsByNickname("부산먹보")).thenReturn(true);

        assertCode(() -> register("dup@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"),
                ErrorCode.EMAIL_DUPLICATED);
        verify(memberRepository, never()).saveAndFlush(any());
    }

    // ---- 비밀번호 검증 ----

    @ParameterizedTest
    @NullAndEmptySource
    void 비밀번호가_없으면_PASSWORD_REQUIRED(String password) {
        assertCode(() -> register("user@example.com", password, password, "부산먹보"), ErrorCode.PASSWORD_REQUIRED);
    }

    @Test
    void 비밀번호_확인값이_없으면_PASSWORD_REQUIRED() {
        assertCode(() -> register("user@example.com", VALID_PASSWORD, null, "부산먹보"), ErrorCode.PASSWORD_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pass1!",          // 6자 (짧음)
            "a1!a1!a1!a1!a1!a1!a1!",   // 21자 (김)
            "12345678!",       // 영문 누락
            "abcdefgh!",       // 숫자 누락
            "abcdefg12",       // 특수문자 누락
            "abc defg1!"       // 공백 포함
    })
    void 비밀번호_조건을_벗어나면_PASSWORD_INVALID(String password) {
        assertCode(() -> register("user@example.com", password, password, "부산먹보"), ErrorCode.PASSWORD_INVALID);
    }

    @Test
    void 비밀번호_확인값이_다르면_PASSWORD_INVALID() {
        assertCode(() -> register("user@example.com", VALID_PASSWORD, "pass1234?", "부산먹보"),
                ErrorCode.PASSWORD_INVALID);
    }

    @Test
    void 비밀번호_오류_메시지에_원문이_포함되지_않는다() {
        String secret = "abc defg1!";
        assertThatThrownBy(() -> register("user@example.com", secret, secret, "부산먹보"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(secret));
    }

    // ---- DB UNIQUE 제약 위반 변환 ----

    @Test
    void 사전_검사_이후_이메일_UNIQUE_위반이면_EMAIL_DUPLICATED로_변환한다() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                uniqueViolation("member.uk_member_email"));

        assertCode(() -> register("race@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"),
                ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    void 사전_검사_이후_닉네임_UNIQUE_위반이면_NICKNAME_DUPLICATED로_변환한다() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                uniqueViolation("member.uk_member_nickname"));

        assertCode(() -> register("race@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"),
                ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void 다른_무결성_오류는_중복_오류로_바꾸지_않고_그대로_전파한다() {
        DataIntegrityViolationException original = uniqueViolation("member.ck_member_role");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(original);

        assertThatThrownBy(() -> register("other@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"))
                .isSameAs(original);
    }

    @Test
    void 제약_이름을_알_수_없으면_원래_예외를_전파한다() {
        DataIntegrityViolationException original = uniqueViolation(null);
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(original);

        assertThatThrownBy(() -> register("other@example.com", VALID_PASSWORD, VALID_PASSWORD, "부산먹보"))
                .isSameAs(original);
    }

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "could not execute statement", new java.sql.SQLException("Duplicate entry"), constraintName);
        return new DataIntegrityViolationException("unique violation", cause);
    }
}
