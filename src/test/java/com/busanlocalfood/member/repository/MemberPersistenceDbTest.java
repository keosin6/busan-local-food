package com.busanlocalfood.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.RefreshToken;
import com.busanlocalfood.member.domain.Role;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL 8 테스트 DB 연동 테스트 (Repository와 DB 제약 검증).
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 db/manual/001_create_member_refresh_token.sql을 미리 적용해야 한다.
 * 개발 DB에는 연결하지 않으며, 각 테스트는 트랜잭션 롤백으로 데이터를 남기지 않는다.
 * H2와 Testcontainers는 사용하지 않는다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class MemberPersistenceDbTest {

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNO";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void 회원을_저장하고_이메일과_Google_sub로_조회한다() {
        String email = "u" + suffix() + "@example.com";
        Member saved = memberRepository.saveAndFlush(
                Member.create(email, HASH, "닉" + suffix(), "sub-" + suffix()));

        assertThat(memberRepository.findByEmail(email)).isPresent();
        assertThat(memberRepository.findByGoogleSub(saved.getGoogleSub())).isPresent();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 닉네임_존재_여부를_확인한다() {
        String nickname = "확인" + suffix().substring(0, 4);
        memberRepository.saveAndFlush(Member.create("n" + suffix() + "@example.com", HASH, nickname, null));

        assertThat(memberRepository.existsByNickname(nickname)).isTrue();
        assertThat(memberRepository.existsByNickname("없는" + suffix())).isFalse();
    }

    @Test
    void Google_전용_회원은_password_hash가_NULL로_저장된다() {
        Member saved = memberRepository.saveAndFlush(Member.create(
                "g" + suffix() + "@example.com", null, "구글" + suffix().substring(0, 4), "gsub-" + suffix()));

        assertThat(memberRepository.findById(saved.getId())).get()
                .extracting(Member::getPasswordHash).isNull();
    }

    @Test
    void 이메일_중복은_DB_UNIQUE_제약이_막는다() {
        String email = "dup" + suffix() + "@example.com";
        memberRepository.saveAndFlush(Member.create(email, HASH, "첫째" + suffix().substring(0, 4), null));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                Member.create(email, HASH, "둘째" + suffix().substring(0, 4), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 닉네임_중복은_DB_UNIQUE_제약이_막는다() {
        String nickname = "중복" + suffix().substring(0, 4);
        memberRepository.saveAndFlush(Member.create("a" + suffix() + "@example.com", HASH, nickname, null));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                Member.create("b" + suffix() + "@example.com", HASH, nickname, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void google_sub_중복은_DB_UNIQUE_제약이_막는다() {
        String sub = "same-sub-" + suffix();
        memberRepository.saveAndFlush(Member.create(
                "s1" + suffix() + "@example.com", null, "가" + suffix().substring(0, 4), sub));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(Member.create(
                "s2" + suffix() + "@example.com", null, "나" + suffix().substring(0, 4), sub)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 로그인_수단이_없는_행은_DB_CHECK_제약이_막는다() {
        // 도메인 생성 메서드는 이 행을 만들 수 없으므로, DB 제약을 직접 확인하기 위해 SQL로 INSERT를 시도한다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO member (email, password_hash, nickname, role, google_sub) VALUES (?, NULL, ?, 'USER', NULL)",
                "nolog" + suffix() + "@example.com", "없음" + suffix().substring(0, 4)))
                // MySQL은 CHECK 위반을 SQLSTATE HY000으로 보고하므로 DataAccessException으로 감싸진다.
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_member_login_method");
    }

    @Test
    void 역할에_허용되지_않은_값은_DB_CHECK_제약이_막는다() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO member (email, password_hash, nickname, role, google_sub) VALUES (?, ?, ?, 'OWNER', NULL)",
                "role" + suffix() + "@example.com", HASH, "역할" + suffix().substring(0, 4)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_member_role");
    }

    @Test
    void Refresh_Token_행을_해시로_조회하고_회원별_활성_토큰을_찾는다() {
        Member member = memberRepository.saveAndFlush(
                Member.create("rt" + suffix() + "@example.com", HASH, "토큰" + suffix().substring(0, 4), null));
        String hash = "a".repeat(64);
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, hash, LocalDateTime.now().plusDays(14)));

        assertThat(refreshTokenRepository.findByTokenHash(hash)).isPresent();
        assertThat(refreshTokenRepository.findAllByMemberIdAndRevokedAtIsNull(member.getId())).hasSize(1);
    }

    @Test
    void 존재하지_않는_회원을_가리키는_Refresh_Token은_FK가_막는다() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO refresh_token (member_id, token_hash, expires_at) VALUES (?, ?, ?)",
                -1L, "c".repeat(64), LocalDateTime.now().plusDays(14)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
