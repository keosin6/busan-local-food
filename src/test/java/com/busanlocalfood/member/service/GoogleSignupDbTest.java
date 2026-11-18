package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.oauth.GoogleSignupPending;
import com.busanlocalfood.member.repository.MemberRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Google 신규 가입의 MySQL 동작 (API_SPEC 2.5.1). 환경 변수 TEST_DB_URL 등이 없으면 건너뛴다.
 * 테스트 DB에는 SQL 001 → 002 → 003을 순서대로 적용해야 한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class GoogleSignupDbTest {

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> randomBase64Key());
        registry.add("auth.rate-limit.hmac-secret", () -> randomBase64Key());
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private GoogleSignupService signupService;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM member");
    }

    private static GoogleSignupPending pending(String sub, String email) {
        return new GoogleSignupPending(sub, email, Instant.now().plusSeconds(600));
    }

    /** 구글 연결이 없는 기존 회원에는 테스트용 더미 해시를 넣는다. 실제 비밀번호가 아니다. */
    private void existingMember(String email, String nickname, String googleSub) {
        String dummyHash = googleSub == null ? "$2a$10$testonlydummyhashvalueforrowsaaaaaaaaaaaaaaaaaaaa" : null;
        members.saveAndFlush(Member.create(email, dummyHash, nickname, googleSub));
    }

    private int memberCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
    }

    @Test
    void 신규_가입은_Google_회원을_만들고_토큰을_발급한다() {
        var issued = signupService.complete(pending("sub-db-1", "new.user@example.com"), "구글신규");

        assertThat(issued.member().getId()).isNotNull();
        var row = jdbc.queryForMap("SELECT role, password_hash, google_sub, email, nickname FROM member WHERE id = ?",
                issued.member().getId());
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("google_sub")).isEqualTo("sub-db-1");
        assertThat(row.get("email")).isEqualTo("new.user@example.com");
        assertThat(row.get("nickname")).isEqualTo("구글신규");
        Integer tokens = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE member_id = ?", Integer.class,
                issued.member().getId());
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void 닉네임_형식이_틀리면_회원을_만들지_않는다() {
        assertThatThrownBy(() -> signupService.complete(pending("sub-db-2", "a@example.com"), "x"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().getStatus().value()).isEqualTo(400));
        assertThat(memberCount()).isZero();
    }

    @Test
    void 닉네임이_이미_있으면_사전_검사에서_거부하고_회원을_만들지_않는다() {
        existingMember("other@example.com", "중복닉네임", null);

        assertThatThrownBy(() -> signupService.complete(pending("sub-db-3", "b@example.com"), "중복닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void 이메일이_이미_있으면_사전_검사에서_거부한다() {
        existingMember("taken@example.com", "기존회원", null);

        assertThatThrownBy(() -> signupService.complete(pending("sub-db-4", "taken@example.com"), "새닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED));
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void 같은_google_sub가_이미_연결되어_있으면_거부한다() {
        existingMember("linked@example.com", "연결회원", "sub-db-5");

        assertThatThrownBy(() -> signupService.complete(pending("sub-db-5", "other@example.com"), "새닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_ALREADY_LINKED));
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void 사전_검사를_건너뛰어도_닉네임_UNIQUE_제약이_중복을_막는다() {
        existingMember("first@example.com", "경쟁닉네임", null);

        assertThatThrownBy(() -> signupService.insertMember(pending("sub-race-1", "second@example.com"), "경쟁닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void 사전_검사를_건너뛰어도_이메일_UNIQUE_제약이_중복을_막는다() {
        existingMember("race@example.com", "먼저닉네임", null);

        assertThatThrownBy(() -> signupService.insertMember(pending("sub-race-2", "race@example.com"), "나중닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED));
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void 사전_검사를_건너뛰어도_google_sub_UNIQUE_제약이_중복을_막는다() {
        existingMember("sub-owner@example.com", "소유회원", "sub-race-3");

        assertThatThrownBy(() -> signupService.insertMember(pending("sub-race-3", "fresh@example.com"), "신규닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_ALREADY_LINKED));
        assertThat(memberCount()).isEqualTo(1);
    }
}
