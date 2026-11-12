package com.busanlocalfood.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.RefreshToken;
import com.busanlocalfood.member.domain.Role;
import jakarta.persistence.EntityManager;
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
 * 테스트 DB에는 다음 두 스크립트를 순서대로 미리 적용해야 한다.
 *   1) db/manual/001_create_member_refresh_token.sql
 *   2) db/manual/002_add_refresh_token_family_id.sql (refresh_token.family_id 컬럼)
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

    /** 기존 백필(SQL 002, MySQL UUID())과 같은 형식의 UUID v1 예시. */
    private static final String V1_FAMILY = "c232ab00-9414-11ec-b3c8-9e6bdeccc4c0";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Member newMember(String prefix) {
        return memberRepository.saveAndFlush(
                Member.create(prefix + suffix() + "@example.com", HASH, "토큰" + suffix().substring(0, 4), null));
    }

    private String storedFamilyId(Long refreshTokenId) {
        return jdbcTemplate.queryForObject(
                "SELECT family_id FROM refresh_token WHERE id = ?", String.class, refreshTokenId);
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
    void family_id_컬럼은_CHAR36_ascii_bin이다() {
        String collation = jdbcTemplate.queryForObject(
                "SELECT COLLATION_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_token' AND COLUMN_NAME = 'family_id'",
                String.class);
        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_token' AND COLUMN_NAME = 'family_id'",
                String.class);

        assertThat(collation).isEqualTo("ascii_bin");
        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void Refresh_Token_행을_해시로_조회하고_회원별_활성_토큰을_찾는다() {
        Member member = newMember("rt");
        String hash = "a".repeat(64);
        refreshTokenRepository.saveAndFlush(
                RefreshToken.issueNewFamily(member, hash, LocalDateTime.now().plusDays(14)));

        assertThat(refreshTokenRepository.findByTokenHashForUpdate(hash)).isPresent();
        assertThat(refreshTokenRepository.findActiveByMemberIdForUpdate(member.getId())).hasSize(1);
    }

    @Test
    void 신규_Family로_저장한_family_id는_DB에_그대로_저장되고_다시_조회된다() {
        Member member = newMember("fam");
        RefreshToken saved = refreshTokenRepository.saveAndFlush(
                RefreshToken.issueNewFamily(member, "d".repeat(64), LocalDateTime.now().plusDays(14)));
        String familyId = saved.getFamilyId();

        // DB에 실제로 쓰인 값을 확인한다.
        assertThat(storedFamilyId(saved.getId())).isEqualTo(familyId);
        assertThat(UUID.fromString(familyId).version()).isEqualTo(4);

        // 영속성 컨텍스트를 비우고 다시 읽어 Entity 매핑 결과를 확인한다.
        entityManager.flush();
        entityManager.clear();
        RefreshToken reloaded = refreshTokenRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void 기존_Family_ID로_만든_회전_토큰은_같은_family_id로_저장된다() {
        Member member = newMember("rot");
        LocalDateTime expires = LocalDateTime.now().plusDays(14);
        RefreshToken first = refreshTokenRepository.saveAndFlush(
                RefreshToken.issueNewFamily(member, "e".repeat(64), expires));

        RefreshToken rotated = refreshTokenRepository.saveAndFlush(
                RefreshToken.issueInFamily(member, first.getFamilyId(), "f".repeat(64), expires));

        assertThat(storedFamilyId(rotated.getId())).isEqualTo(first.getFamilyId());
        Integer sameFamilyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE family_id = ?", Integer.class, first.getFamilyId());
        assertThat(sameFamilyRows).isEqualTo(2);
    }

    @Test
    void SQL_002로_백필된_UUID_v1_family_id도_그대로_저장되고_조회된다() {
        Member member = newMember("v1");
        RefreshToken saved = refreshTokenRepository.saveAndFlush(
                RefreshToken.issueInFamily(member, V1_FAMILY, "1".repeat(64), LocalDateTime.now().plusDays(14)));

        assertThat(storedFamilyId(saved.getId())).isEqualTo(V1_FAMILY);
        entityManager.flush();
        entityManager.clear();
        assertThat(refreshTokenRepository.findById(saved.getId())).get()
                .extracting(RefreshToken::getFamilyId).isEqualTo(V1_FAMILY);
    }

    @Test
    void family_id를_빼고_INSERT하면_NOT_NULL_제약이_막는다() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO refresh_token (member_id, token_hash, expires_at) VALUES (?, ?, ?)",
                -1L, "2".repeat(64), LocalDateTime.now().plusDays(14)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("family_id");
    }

    @Test
    void 존재하지_않는_회원을_가리키는_Refresh_Token은_FK가_막는다() {
        // family_id에는 유효한 UUID v4를 넣어, NOT NULL이 아니라 FK 위반이 실제로 발생하도록 한다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO refresh_token (member_id, token_hash, family_id, expires_at) VALUES (?, ?, ?, ?)",
                -1L, "c".repeat(64), UUID.randomUUID().toString(), LocalDateTime.now().plusDays(14)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_refresh_member");
    }
}
