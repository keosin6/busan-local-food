package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import com.busanlocalfood.global.config.PasswordEncoderConfig;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 실제 MySQL 8 테스트 DB에서 회원가입 서비스의 UNIQUE 위반 변환을 확인한다.
 * 사전 중복 검사를 spy로 "없음"을 돌려주게 하여 동시 가입 경합을 재현한다.
 * TEST_DB_URL 등 환경 변수가 없으면 건너뛴다. 테스트 DB에는 수동 DDL이 적용되어 있어야 한다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({MemberRegistrationService.class, PasswordEncoderConfig.class})
class MemberRegistrationDbTest {

    private static final String PASSWORD = "pass1234!";

    @Autowired
    private MemberRegistrationService registrationService;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private MemberRepository spiedRepository;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void 정상_가입은_DB에_BCrypt_해시로_저장된다() {
        String email = "ok" + suffix() + "@example.com";

        var saved = registrationService.register(email, PASSWORD, PASSWORD, "정상" + suffix().substring(0, 4));

        assertThat(saved.getId()).isNotNull();
        assertThat(memberRepository.findByEmail(email)).get()
                .satisfies(m -> assertThat(m.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD));
    }

    @Test
    void 사전_검사_이후_이메일_경합이면_DB_UNIQUE가_막고_EMAIL_DUPLICATED를_반환한다() {
        String email = "race" + suffix() + "@example.com";
        registrationService.register(email, PASSWORD, PASSWORD, "먼저" + suffix().substring(0, 4));

        // 사전 검사가 "없음"이라고 답하도록 하여 동시 요청이 끼어든 상황을 재현한다.
        doReturn(false).when(spiedRepository).existsByEmail(anyString());

        assertThatThrownBy(() -> registrationService.register(
                email, PASSWORD, PASSWORD, "나중" + suffix().substring(0, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_DUPLICATED));
    }

    @Test
    void 사전_검사_이후_닉네임_경합이면_DB_UNIQUE가_막고_NICKNAME_DUPLICATED를_반환한다() {
        String nickname = "경합" + suffix().substring(0, 4);
        registrationService.register("first" + suffix() + "@example.com", PASSWORD, PASSWORD, nickname);

        doReturn(false).when(spiedRepository).existsByNickname(anyString());

        assertThatThrownBy(() -> registrationService.register(
                "second" + suffix() + "@example.com", PASSWORD, PASSWORD, nickname))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
    }
}
