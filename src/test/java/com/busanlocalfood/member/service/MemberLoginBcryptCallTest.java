package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인 검증에서 BCrypt 검증 호출 여부와 횟수를 확인한다.
 * 응답 시간을 직접 재지 않고, 모든 경로에서 matches가 정확히 한 번 호출되는지로 검증한다.
 */
class MemberLoginBcryptCallTest {

    private static final String RAW_PASSWORD = "pass1234!";
    private static final String DUMMY_HASH = "$2a$10$dummy-hash-for-test";
    private static final String REAL_HASH = "$2a$10$real-hash-for-test";

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private MemberLoginService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        // 생성자에서 더미 해시를 만들 때 사용하는 encode 결과를 고정한다.
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        service = new MemberLoginService(memberRepository, passwordEncoder);
    }

    @Test
    void 미가입_이메일도_더미_해시로_BCrypt_검증을_정확히_한_번_수행한다() {
        when(memberRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("none@example.com", RAW_PASSWORD))
                .isInstanceOf(BusinessException.class);

        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, DUMMY_HASH);
    }

    @Test
    void Google_전용_계정도_더미_해시로_BCrypt_검증을_정확히_한_번_수행한다() {
        Member googleOnly = Member.create("g@example.com", null, "구글회원", "google-sub-1");
        when(memberRepository.findByEmail("g@example.com")).thenReturn(Optional.of(googleOnly));

        assertThatThrownBy(() -> service.authenticate("g@example.com", RAW_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, DUMMY_HASH);
    }

    @Test
    void 일반_회원은_저장된_해시로_BCrypt_검증을_정확히_한_번_수행한다() {
        Member member = Member.create("u@example.com", REAL_HASH, "부산먹보", null);
        when(memberRepository.findByEmail("u@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(eq(RAW_PASSWORD), eq(REAL_HASH))).thenReturn(true);

        assertThat(service.authenticate("u@example.com", RAW_PASSWORD)).isSameAs(member);

        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, REAL_HASH);
        verify(passwordEncoder, times(0)).matches(RAW_PASSWORD, DUMMY_HASH);
    }

    @Test
    void 일반_회원의_비밀번호_불일치도_BCrypt_검증을_정확히_한_번_수행한다() {
        Member member = Member.create("u@example.com", REAL_HASH, "부산먹보", null);
        when(memberRepository.findByEmail("u@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(eq("wrong-pass1!"), eq(REAL_HASH))).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("u@example.com", "wrong-pass1!"))
                .isInstanceOf(BusinessException.class);

        verify(passwordEncoder, times(1)).matches("wrong-pass1!", REAL_HASH);
    }

    @Test
    void 미가입_Google_전용_비밀번호_불일치의_외부_응답이_모두_같다() {
        Member googleOnly = Member.create("g@example.com", null, "구글회원", "google-sub-1");
        Member member = Member.create("u@example.com", REAL_HASH, "부산먹보", null);
        when(memberRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());
        when(memberRepository.findByEmail("g@example.com")).thenReturn(Optional.of(googleOnly));
        when(memberRepository.findByEmail("u@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(anyString(), eq(REAL_HASH))).thenReturn(false);

        BusinessException notFound = failure("none@example.com", RAW_PASSWORD);
        BusinessException googleOnlyFailure = failure("g@example.com", RAW_PASSWORD);
        BusinessException wrongPassword = failure("u@example.com", "wrong-pass1!");

        assertThat(googleOnlyFailure.getErrorCode()).isEqualTo(notFound.getErrorCode());
        assertThat(wrongPassword.getErrorCode()).isEqualTo(notFound.getErrorCode());
        assertThat(googleOnlyFailure.getMessage()).isEqualTo(notFound.getMessage());
        assertThat(wrongPassword.getMessage()).isEqualTo(notFound.getMessage());
    }

    private BusinessException failure(String email, String password) {
        try {
            service.authenticate(email, password);
        } catch (BusinessException e) {
            return e;
        }
        throw new AssertionError("BusinessException이 발생하지 않았습니다.");
    }
}
