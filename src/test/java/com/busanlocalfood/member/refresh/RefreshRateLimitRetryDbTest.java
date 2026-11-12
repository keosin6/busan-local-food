package com.busanlocalfood.member.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.ratelimit.AuthRateLimitCleanup;
import com.busanlocalfood.member.ratelimit.AuthRateLimitConfig;
import com.busanlocalfood.member.ratelimit.AuthRateLimitHasher;
import com.busanlocalfood.member.ratelimit.AuthRateLimitRepository;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import com.busanlocalfood.member.ratelimit.RateLimitUnavailableException;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.repository.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.busanlocalfood.member.refresh.RefreshTokenRateLimitGate;

/**
 * Refresh 회전의 잠금 재시도 (PR #16 보완 1)를 MySQL 8에서 검증한다.
 * 카운터 잠금 실패와 재사용 탐지 경로의 잠금 실패를 재현한다. 재시도는 새 트랜잭션에서 처음부터 다시 실행된다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 SQL 001 → 002 → 003을 순서대로 적용해야 한다. 이 클래스가 만든 행은 @AfterEach에서 지운다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RefreshTokenService.class, RefreshTokenGenerator.class, RefreshTokenConfig.class,
        TimeConfig.class, AuthRateLimitConfig.class, AuthRateLimitRepository.class,
        AuthRateLimitCleanup.class, AuthRateLimitService.class, RefreshTokenRateLimitGate.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshRateLimitRetryDbTest {

    private static final String HASH_PW = "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNO";

    @DynamicPropertySource
    static void rateLimitSecret(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);
        registry.add("auth.rate-limit.hmac-secret", () -> secret);
    }

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthRateLimitRepository rateLimitRepository;

    @Autowired
    private AuthRateLimitHasher hasher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /** 카운터 잠금 단계(게이트)를 감싸 실패를 재현한다. 기본 동작은 실제 게이트 호출이다. */
    @MockitoSpyBean
    private RefreshTokenRateLimitGate gate;

    /** 재사용 탐지의 활성 토큰 잠금 단계를 감싸 한 번 실패를 재현한다. */
    @MockitoSpyBean
    private RefreshTokenRepository refreshTokenRepository;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long memberId : memberIds) {
            for (String familyId : jdbc.queryForList(
                    "SELECT DISTINCT family_id FROM refresh_token WHERE member_id = ?", String.class, memberId)) {
                jdbc.update("DELETE FROM refresh_request_event WHERE family_id = ?", familyId);
                jdbc.update("DELETE FROM auth_rate_limit_counter WHERE counter_key = ?",
                        hasher.counterKey("REFRESH_FAMILY:", familyId));
            }
            jdbc.update("DELETE FROM refresh_token WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE id = ?", memberId);
        }
        memberIds.clear();
    }

    private Member newMember() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member saved = memberRepository.saveAndFlush(
                Member.create("retry" + suffix + "@example.com", HASH_PW, "재시" + suffix.substring(0, 4), null));
        memberIds.add(saved.getId());
        return saved;
    }

    private String familyOf(long memberId) {
        return jdbc.queryForObject("SELECT family_id FROM refresh_token WHERE member_id = ? LIMIT 1",
                String.class, memberId);
    }

    private int activeCount(long memberId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE member_id = ? AND revoked_at IS NULL", Integer.class, memberId);
        return n == null ? 0 : n;
    }

    private int eventCount(String familyId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_request_event WHERE family_id = ?", Integer.class, familyId);
        return n == null ? 0 : n;
    }

    @Test
    void 카운터_잠금_실패는_새_트랜잭션에서_재시도되고_실패한_시도의_이벤트는_남지_않는다() {
        Member member = newMember();
        String raw = service.issueForNewLogin(member.getId()).token();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = calls.incrementAndGet();
            // 실제 게이트가 이벤트를 먼저 기록한 뒤 잠금 실패를 던진다. 롤백으로 그 이벤트가 사라져야 한다.
            RateLimitDecision decision = (RateLimitDecision) invocation.callRealMethod();
            if (attempt <= 2) {
                throw new PessimisticLockingFailureException("simulated lock wait timeout");
            }
            return decision;
        }).when(gate).check(anyString());

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rotated.class);
        assertThat(calls.get()).isEqualTo(3);
        String family = familyOf(member.getId());
        assertThat(eventCount(family)).isEqualTo(1);
        assertThat(activeCount(member.getId())).isEqualTo(1);
    }

    @Test
    void 재시도_한도를_넘기면_요청을_허용하지_않고_토큰과_이벤트를_바꾸지_않는다() {
        Member member = newMember();
        String raw = service.issueForNewLogin(member.getId()).token();
        String family = familyOf(member.getId());
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            invocation.callRealMethod();
            throw new PessimisticLockingFailureException("simulated deadlock");
        }).when(gate).check(anyString());

        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(RateLimitUnavailableException.class);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(activeCount(member.getId())).isEqualTo(1);
        assertThat(eventCount(family)).isZero();
    }

    @Test
    void 제한된_Refresh_요청은_토큰을_회전하거나_폐기하지_않는다() {
        Member member = newMember();
        String raw = service.issueForNewLogin(member.getId()).token();
        String family = familyOf(member.getId());
        for (int i = 0; i < 10; i++) {
            rateLimitRepository.insertRefreshRequest(family, LocalDateTime.now());
        }

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.RateLimited.class);
        long retry = ((RefreshTokenRotation.RateLimited) result).retryAfterSeconds();
        assertThat(retry).isBetween(1L, 60L);
        assertThat(activeCount(member.getId())).isEqualTo(1);
        assertThat(eventCount(family)).isEqualTo(10);
    }

    @Test
    void 정상_회전은_잠금_재시도가_없을_때_이벤트를_한_번만_기록한다() {
        Member member = newMember();
        String raw = service.issueForNewLogin(member.getId()).token();

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rotated.class);
        assertThat(eventCount(familyOf(member.getId()))).isEqualTo(1);
        assertThat(activeCount(member.getId())).isEqualTo(1);
    }

    @Test
    void 재사용_탐지의_활성_토큰_잠금이_실패해도_재시도_후_전체_폐기가_커밋된다() {
        Member member = newMember();
        String original = service.issueForNewLogin(member.getId()).token();
        RefreshTokenRotation first = service.rotate(original);
        assertThat(first).isInstanceOf(RefreshTokenRotation.Rotated.class);
        assertThat(activeCount(member.getId())).isEqualTo(1);

        AtomicInteger calls = new AtomicInteger();
        // 인터페이스 프록시는 실제 메서드를 직접 부를 수 없으므로, 두 번째 호출은 같은 JPQL을 잠금 조회로 실행한다.
        // 같은 트랜잭션의 영속성 컨텍스트에서 읽으므로 서비스가 이 엔티티를 폐기하면 커밋에 반영된다.
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                throw new PessimisticLockingFailureException("simulated lock wait timeout");
            }
            Long memberId = invocation.getArgument(0);
            return entityManager.createQuery(
                            "select rt from RefreshToken rt where rt.member.id = :memberId and rt.revokedAt is null order by rt.id",
                            com.busanlocalfood.member.domain.RefreshToken.class)
                    .setParameter("memberId", memberId)
                    .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
        }).when(refreshTokenRepository).findActiveByMemberIdForUpdate(anyLong());

        RefreshTokenRotation reuse = service.rotate(original);

        assertThat(reuse).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(((RefreshTokenRotation.ReuseDetected) reuse).revokedCount()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(2);
        // 같은 DB를 새 조회로 확인한다. 롤백되었다면 활성 토큰이 남는다.
        assertThat(activeCount(member.getId())).isZero();
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE member_id = ?", Integer.class, member.getId());
        assertThat(rows).isEqualTo(2);
    }
}
