package com.busanlocalfood.member.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 제한의 MySQL 동작을 검증한다 (SQL 003, 행 잠금, 경계값, 정리).
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 SQL 001 → 002 → 003을 순서대로 적용해야 한다. 요청 제한 테이블은 테스트 전후로 비운다.
 * 이 테스트 클래스는 테스트 DB 전용이다. 개발 DB에는 연결하지 않는다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({AuthRateLimitConfig.class, AuthRateLimitRepository.class, AuthRateLimitCleanup.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthRateLimitDbTest {

    private static final String EMAIL = "limit@example.com";
    private static final String IP = "203.0.113.9";
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 12, 0);

    @DynamicPropertySource
    static void hmacSecret(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);
        registry.add("auth.rate-limit.hmac-secret", () -> secret);
    }

    @Autowired
    private AuthRateLimitRepository repository;

    @Autowired
    private AuthRateLimitHasher hasher;

    @Autowired
    private AuthRateLimitCleanup cleanup;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private MutableClock clock;
    private AuthRateLimitService service;

    /** 테스트마다 값을 바꿀 수 있는 시계. 여러 스레드가 읽으므로 volatile이다. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone = ZoneId.of("Asia/Seoul");

        MutableClock(LocalDateTime start) {
            this.instant = start.atZone(zone).toInstant();
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        clearTables();
        clock = new MutableClock(START);
        service = new AuthRateLimitService(repository, hasher, new LockRetryTemplate(transactionManager), clock, cleanup);
    }

    @AfterEach
    void tearDown() {
        clearTables();
    }

    private void clearTables() {
        jdbc.update("DELETE FROM login_failure_event");
        jdbc.update("DELETE FROM signup_request_event");
        jdbc.update("DELETE FROM refresh_request_event");
        jdbc.update("DELETE FROM google_oauth_request_event");
        jdbc.update("DELETE FROM auth_rate_limit_counter");
    }

    private LoginAttemptResult failLogin(String email, String ip) {
        return service.attemptLogin(email, ip, () -> false);
    }

    private int loginRows(String ip) {
        return count("SELECT COUNT(*) FROM login_failure_event WHERE ip_key = ?", hasher.hmac(ip));
    }

    private int count(String sql, String param) {
        Integer n = jdbc.queryForObject(sql, Integer.class, param);
        return n == null ? 0 : n;
    }

    @Test
    void 로그인_실패는_다섯번까지_허용하고_여섯번째는_자격_증명을_확인하지_않고_제한한다() {
        AtomicInteger checks = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThat(service.attemptLogin(EMAIL, IP, () -> {
                checks.incrementAndGet();
                return false;
            })).isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
        }

        LoginAttemptResult sixth = service.attemptLogin(EMAIL, IP, () -> {
            checks.incrementAndGet();
            return true;
        });

        assertThat(sixth).isEqualTo(new LoginAttemptResult.Limited(15 * 60));
        assertThat(checks.get()).isEqualTo(5);
    }

    @Test
    void 제한된_로그인_요청은_이벤트를_추가하지_않는다() {
        for (int i = 0; i < 5; i++) {
            failLogin(EMAIL, IP);
        }

        failLogin(EMAIL, IP);
        failLogin(EMAIL, IP);

        assertThat(loginRows(IP)).isEqualTo(5);
    }

    @Test
    void IP_보조_제한은_서로_다른_이메일의_실패를_합산한다() {
        for (int i = 0; i < 20; i++) {
            assertThat(failLogin("user" + i + "@example.com", IP))
                    .isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
        }

        assertThat(failLogin("another@example.com", IP)).isInstanceOf(LoginAttemptResult.Limited.class);
        assertThat(loginRows(IP)).isEqualTo(20);
    }

    @Test
    void 같은_이메일이라도_IP가_다르면_따로_집계한다() {
        for (int i = 0; i < 5; i++) {
            failLogin(EMAIL, IP);
        }

        assertThat(failLogin(EMAIL, IP)).isInstanceOf(LoginAttemptResult.Limited.class);
        assertThat(failLogin(EMAIL, "198.51.100.77")).isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
    }

    @Test
    void 성공_로그인은_이메일과_IP_조합의_실패만_지우고_같은_IP의_다른_이메일_기록은_남긴다() {
        for (int i = 0; i < 3; i++) {
            failLogin(EMAIL, IP);
        }
        for (int i = 0; i < 2; i++) {
            failLogin("other@example.com", IP);
        }
        assertThat(loginRows(IP)).isEqualTo(5);

        LoginAttemptResult success = service.attemptLogin(EMAIL, IP, () -> true);

        assertThat(success).isInstanceOf(LoginAttemptResult.Authenticated.class);
        assertThat(loginRows(IP)).isEqualTo(2);
        // 초기화 후에는 같은 조합이 다시 다섯 번까지 실패할 수 있다.
        for (int i = 0; i < 5; i++) {
            assertThat(failLogin(EMAIL, IP)).isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
        }
        assertThat(failLogin(EMAIL, IP)).isInstanceOf(LoginAttemptResult.Limited.class);
    }

    @Test
    void 로그인_제한은_15분_창이_지나면_다시_허용된다() {
        for (int i = 0; i < 5; i++) {
            failLogin(EMAIL, IP);
        }

        clock.advance(Duration.ofMinutes(1));
        assertThat(failLogin(EMAIL, IP)).isEqualTo(new LoginAttemptResult.Limited(14 * 60));

        clock.advance(Duration.ofMinutes(13).plusSeconds(59));
        assertThat(failLogin(EMAIL, IP)).isEqualTo(new LoginAttemptResult.Limited(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(failLogin(EMAIL, IP)).isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
    }

    @Test
    void 회원가입은_IP별_한시간_다섯번까지_허용하고_경계_뒤에_다시_허용된다() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.recordSignupRequest(IP)).isInstanceOf(RateLimitDecision.Allowed.class);
        }

        assertThat(service.recordSignupRequest(IP)).isEqualTo(new RateLimitDecision.Limited(60 * 60));
        assertThat(service.recordSignupRequest("198.51.100.77")).isInstanceOf(RateLimitDecision.Allowed.class);

        clock.advance(Duration.ofHours(1));
        assertThat(service.recordSignupRequest(IP)).isInstanceOf(RateLimitDecision.Allowed.class);
    }

    @Test
    void Google_OAuth2는_IP별_열번까지_허용하고_회원가입_카운터와_독립이다() {
        for (int i = 0; i < 10; i++) {
            assertThat(service.recordGoogleOAuthRequest(IP)).isInstanceOf(RateLimitDecision.Allowed.class);
        }

        assertThat(service.recordGoogleOAuthRequest(IP)).isEqualTo(new RateLimitDecision.Limited(15 * 60));
        assertThat(service.recordSignupRequest(IP)).isInstanceOf(RateLimitDecision.Allowed.class);
    }

    @Test
    void Refresh는_계열별_1분_열번까지_허용하고_다른_계열은_독립이다() {
        String familyA = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        String familyB = "c232ab00-9414-11ec-b3c8-9e6bdeccc4c0";
        for (int i = 0; i < 10; i++) {
            assertThat(service.recordRefreshRequest(familyA)).isInstanceOf(RateLimitDecision.Allowed.class);
        }

        assertThat(service.recordRefreshRequest(familyA)).isEqualTo(new RateLimitDecision.Limited(60));
        assertThat(service.recordRefreshRequest(familyB)).isInstanceOf(RateLimitDecision.Allowed.class);

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.recordRefreshRequest(familyA)).isInstanceOf(RateLimitDecision.Allowed.class);
    }

    @Test
    void 동시_로그인_실패는_같은_이메일과_IP에서_다섯번만_자격_증명을_확인한다() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        List<LoginAttemptResult> results = runConcurrently(20, i -> () -> service.attemptLogin(EMAIL, IP, () -> {
            checks.incrementAndGet();
            sleep(50); // BCrypt 검증 시간을 흉내 낸다. 잠금이 이 시간 동안 유지되어야 한다.
            return false;
        }));

        assertThat(checks.get()).isEqualTo(5);
        assertThat(results).filteredOn(r -> r instanceof LoginAttemptResult.InvalidCredentials).hasSize(5);
        assertThat(results).filteredOn(r -> r instanceof LoginAttemptResult.Limited).hasSize(15);
        assertThat(loginRows(IP)).isEqualTo(5);
    }

    @Test
    void 동시_로그인_실패는_같은_IP에서_스무번만_자격_증명을_확인한다() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        List<LoginAttemptResult> results = runConcurrently(30, i -> () ->
                service.attemptLogin("user" + i + "@example.com", IP, () -> {
                    checks.incrementAndGet();
                    sleep(20);
                    return false;
                }));

        assertThat(checks.get()).isEqualTo(20);
        assertThat(results).filteredOn(r -> r instanceof LoginAttemptResult.Limited).hasSize(10);
        assertThat(loginRows(IP)).isEqualTo(20);
    }

    @Test
    void 동시_회원가입_요청은_IP별_다섯번만_허용한다() throws Exception {
        List<RateLimitDecision> results = runConcurrently(12, i -> () -> service.recordSignupRequest(IP));

        assertThat(results).filteredOn(r -> r instanceof RateLimitDecision.Allowed).hasSize(5);
        assertThat(count("SELECT COUNT(*) FROM signup_request_event WHERE ip_key = ?", hasher.hmac(IP))).isEqualTo(5);
    }

    @Test
    void 동시_Refresh_요청은_계열별_열번만_허용한다() throws Exception {
        String family = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        List<RateLimitDecision> results = runConcurrently(15, i -> () -> service.recordRefreshRequest(family));

        assertThat(results).filteredOn(r -> r instanceof RateLimitDecision.Allowed).hasSize(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_request_event WHERE family_id = ?",
                Integer.class, family)).isEqualTo(10);
    }

    @Test
    void 서로_겹치는_키의_동시_로그인에서도_교착_없이_한도를_지킨다() throws Exception {
        String[] emails = {"a@example.com", "b@example.com", "c@example.com", "d@example.com"};
        List<LoginAttemptResult> results = runConcurrently(40, i -> () ->
                service.attemptLogin(emails[i % 4], "198.51.100." + (i % 10), () -> {
                    sleep(5);
                    return false;
                }));

        assertThat(results).hasSize(40);
        assertThat(results).noneMatch(r -> r == null);
        for (int ip = 0; ip < 10; ip++) {
            assertThat(loginRows("198.51.100." + ip)).isLessThanOrEqualTo(10);
        }
    }

    @Test
    void 보존_기간이_지난_이벤트만_정리하고_카운터_행은_남긴다() {
        LocalDateTime now = START;
        repository.insertLoginFailure(hasher.hmac("p1"), hasher.hmac(IP), now.minusHours(25));
        repository.insertLoginFailure(hasher.hmac("p2"), hasher.hmac(IP), now.minusHours(23));
        repository.insertSignupRequest(hasher.hmac(IP), now.minusHours(25));
        repository.insertRefreshRequest("f47ac10b-58cc-4372-a567-0e02b2c3d479", now.minusHours(25));
        repository.insertGoogleOAuthRequest(hasher.hmac(IP), now.minusHours(25));
        jdbc.update("INSERT INTO auth_rate_limit_counter (counter_key) VALUES (?)", "a".repeat(64));

        int deleted = cleanup.purgeExpired(now, 2, 10);

        assertThat(deleted).isEqualTo(4);
        assertThat(loginRows(IP)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_rate_limit_counter", Integer.class)).isEqualTo(1);
    }

    @Test
    void 정리는_배치_크기만큼만_지우고_반복_실행해도_안전하다() {
        for (int i = 0; i < 5; i++) {
            repository.insertSignupRequest(hasher.hmac(IP), START.minusHours(30));
        }

        assertThat(cleanup.purgeOneBatch(EventTable.SIGNUP_REQUEST, START, 2)).isEqualTo(2);
        assertThat(cleanup.purgeExpired(START, 2, 10)).isEqualTo(3);
        assertThat(cleanup.purgeExpired(START, 2, 10)).isZero();
    }

    @Test
    void 요청_시_정리가_실패해도_로그인_결과와_이벤트_기록은_그대로다() {
        AuthRateLimitCleanup failing = new AuthRateLimitCleanup(repository) {
            @Override
            public int purgeOneBatch(EventTable table, LocalDateTime now, int batchSize) {
                throw new IllegalStateException("정리 실패 시뮬레이션");
            }
        };
        AuthRateLimitService serviceWithFailingCleanup =
                new AuthRateLimitService(repository, hasher, new LockRetryTemplate(transactionManager), clock, failing);

        LoginAttemptResult result = serviceWithFailingCleanup.attemptLogin(EMAIL, IP, () -> false);

        assertThat(result).isInstanceOf(LoginAttemptResult.InvalidCredentials.class);
        assertThat(loginRows(IP)).isEqualTo(1);
        assertThatCode(() -> serviceWithFailingCleanup.recordSignupRequest(IP)).doesNotThrowAnyException();
    }

    @FunctionalInterface
    private interface Task<T> {
        Callable<T> at(int index);
    }

    /** 같은 시작 신호로 여러 스레드를 동시에 출발시키고 모든 결과를 모은다. */
    private static <T> List<T> runConcurrently(int threads, Task<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> body = task.at(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return body.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
