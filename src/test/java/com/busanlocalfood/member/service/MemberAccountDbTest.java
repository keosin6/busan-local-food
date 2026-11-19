package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.MemberAccountService.LinkOutcome;
import com.busanlocalfood.member.service.MemberAccountService.PasswordOutcome;
import com.busanlocalfood.member.service.MemberAccountService.UnlinkOutcome;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 계정 연결·해제·비밀번호 설정의 MySQL 동작 (API_SPEC 2.6.1, 2.7, 2.7.1).
 * 환경 변수 TEST_DB_URL 등이 없으면 건너뛴다. 테스트 DB에는 SQL 001 → 002 → 003을 적용해야 한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class MemberAccountDbTest {

    private static final String PASSWORD = "Abcdef1!";

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
    private MemberAccountService accounts;

    @Autowired
    private MemberRepository members;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private ExecutorService pool;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM member");
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private Member saved(String email, String nickname, String passwordHash, String googleSub) {
        return members.saveAndFlush(Member.create(email, passwordHash, nickname, googleSub));
    }

    private String hashOf(long memberId) {
        return jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, memberId);
    }

    private String googleSubOf(long memberId) {
        return jdbc.queryForObject("SELECT google_sub FROM member WHERE id = ?", String.class, memberId);
    }

    private int countGoogleSub(String sub) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE google_sub = ?", Integer.class, sub);
    }

    /** 두 작업을 같은 순간에 시작시키고 결과를 모은다. */
    private <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<T> a = pool.submit(() -> {
            start.await();
            return first.call();
        });
        Future<T> b = pool.submit(() -> {
            start.await();
            return second.call();
        });
        start.countDown();
        List<T> results = new ArrayList<>();
        results.add(a.get(30, TimeUnit.SECONDS));
        results.add(b.get(30, TimeUnit.SECONDS));
        return results;
    }

    @Test
    void 같은_Google_전용_회원에_대한_동시_비밀번호_설정은_한_번만_성공하고_해시만_남는다() throws Exception {
        Member googleOnly = saved("g-only@example.com", "구글전용", null, "sub-pw-race");

        List<PasswordOutcome> outcomes = runTogether(
                () -> accounts.setLocalPassword(googleOnly.getId(), PASSWORD, PASSWORD),
                () -> accounts.setLocalPassword(googleOnly.getId(), "Zyxwvu9#", "Zyxwvu9#"));

        assertThat(outcomes).containsExactlyInAnyOrder(PasswordOutcome.SET, PasswordOutcome.ALREADY_SET);
        String stored = hashOf(googleOnly.getId());
        assertThat(stored).startsWith("$2");
        assertThat(stored).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored)
                || passwordEncoder.matches("Zyxwvu9#", stored)).isTrue();
        assertThat(googleSubOf(googleOnly.getId())).isEqualTo("sub-pw-race");
    }

    @Test
    void 비밀번호가_이미_있으면_설정_요청은_ALREADY_SET이고_해시를_바꾸지_않는다() {
        Member member = saved("has-pw@example.com", "비번있음", passwordEncoder.encode(PASSWORD), null);
        String before = hashOf(member.getId());

        assertThat(accounts.setLocalPassword(member.getId(), "Zyxwvu9#", "Zyxwvu9#"))
                .isEqualTo(PasswordOutcome.ALREADY_SET);
        assertThat(hashOf(member.getId())).isEqualTo(before);
    }

    @Test
    void 같은_Google_sub를_두_회원이_동시에_연결하면_한쪽만_성공하고_UNIQUE가_최종_방어한다() throws Exception {
        Member first = saved("link-a@example.com", "연결A", passwordEncoder.encode(PASSWORD), null);
        Member second = saved("link-b@example.com", "연결B", passwordEncoder.encode(PASSWORD), null);
        String sub = "sub-race-link";

        List<Object> outcomes = runTogether(
                () -> linkOrError(first.getId(), sub),
                () -> linkOrError(second.getId(), sub));

        long linked = outcomes.stream().filter(o -> o == LinkOutcome.LINKED).count();
        long rejected = outcomes.stream()
                .filter(o -> o == LinkOutcome.ALREADY_LINKED_TO_OTHER || o == ErrorCode.GOOGLE_ALREADY_LINKED)
                .count();
        assertThat(linked).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(countGoogleSub(sub)).isEqualTo(1);
    }

    /** 연결 결과를 값으로 바꾼다. 중복 예외는 ErrorCode로 받는다. */
    private Object linkOrError(long memberId, String sub) {
        try {
            return accounts.linkGoogle(memberId, sub);
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    @Test
    void 같은_회원이_같은_sub를_다시_연결해도_한_행만_남는다() {
        Member member = saved("idem@example.com", "멱등", passwordEncoder.encode(PASSWORD), null);

        assertThat(accounts.linkGoogle(member.getId(), "sub-idem")).isEqualTo(LinkOutcome.LINKED);
        assertThat(accounts.linkGoogle(member.getId(), "sub-idem")).isEqualTo(LinkOutcome.LINKED);
        assertThat(countGoogleSub("sub-idem")).isEqualTo(1);
    }

    @Test
    void 해제는_google_sub를_NULL로_만들고_비밀번호는_그대로_둔다() {
        Member member = saved("unlink@example.com", "해제", passwordEncoder.encode(PASSWORD), "sub-unlink");

        assertThat(accounts.unlinkGoogle(member.getId(), PASSWORD)).isEqualTo(UnlinkOutcome.UNLINKED);
        assertThat(googleSubOf(member.getId())).isNull();
        assertThat(hashOf(member.getId())).isNotNull();
    }

    @Test
    void Google_전용_회원의_해제는_거부되고_DB를_바꾸지_않는다() {
        Member googleOnly = saved("only-g@example.com", "전용", null, "sub-only");

        assertThat(accounts.unlinkGoogle(googleOnly.getId(), PASSWORD)).isEqualTo(UnlinkOutcome.PASSWORD_REQUIRED);
        assertThat(googleSubOf(googleOnly.getId())).isEqualTo("sub-only");
    }

    @Test
    void 설정_후에도_기존_Google_연결은_유지된다() {
        Member googleOnly = saved("keep@example.com", "유지", null, "sub-keep");

        assertThat(accounts.setLocalPassword(googleOnly.getId(), PASSWORD, PASSWORD)).isEqualTo(PasswordOutcome.SET);
        assertThat(googleSubOf(googleOnly.getId())).isEqualTo("sub-keep");
    }
}
