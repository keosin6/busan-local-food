package com.busanlocalfood.member.ratelimit;

import com.busanlocalfood.member.domain.Member;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.busanlocalfood.member.ratelimit.AuthRateLimitPolicy.*;

/**
 * 인증 요청 제한 서비스 (API_SPEC 1.4, DATABASE_ERD 2.10).
 *
 * 동시성 규칙:
 * - 판정의 원천은 이벤트 테이블이다. 카운터 행은 같은 키의 요청을 직렬화하는 잠금 행이다.
 * - 같은 트랜잭션에서 카운터 행을 사전순으로 잠근 뒤 이벤트 수를 센다. 한도 미만이면 이벤트를 기록하고 커밋한다.
 * - 로그인은 자격 증명 확인까지 같은 잠금 안에서 한다. 잠금 밖에서 세고 나중에 기록하면 동시 요청으로 한도를 넘길 수 있다.
 *   그래서 BCrypt 검증 시간만큼 잠금이 유지된다. 같은 이메일+IP, 같은 IP의 로그인은 이 시간만큼 직렬화된다.
 * - 제한된 요청은 이벤트를 추가하지 않는다. 예외로 롤백하지 않고 결과 값으로 구분한다.
 * - 잠금 대기 또는 교착 상태는 최대 MAX_LOCK_ATTEMPTS번 다시 시도한다. 계속 실패하면 요청을 허용하지 않는다 (fail-closed).
 * - 이미 외부 트랜잭션(Refresh 회전)이 열려 있으면 그 트랜잭션에 참여하고, 재시도와 요청 시 정리를 하지 않는다.
 *
 * 로그와 예외 메시지에는 이메일, IP, HMAC 값, 카운터 키, family_id를 남기지 않는다.
 */
@Service
public class AuthRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitService.class);

    /** 요청 시 정리의 배치 크기. 요청 경로에서 오래 걸리지 않도록 작게 둔다. */
    static final int REQUEST_PURGE_BATCH = 100;
    static final int MAX_LOCK_ATTEMPTS = 3;

    private final AuthRateLimitRepository repository;
    private final AuthRateLimitHasher hasher;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AuthRateLimitCleanup cleanup;

    public AuthRateLimitService(AuthRateLimitRepository repository,
                                AuthRateLimitHasher hasher,
                                PlatformTransactionManager transactionManager,
                                Clock clock,
                                AuthRateLimitCleanup cleanup) {
        this.repository = repository;
        this.hasher = hasher;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.cleanup = cleanup;
    }

    /**
     * 로그인 시도 1회를 처리한다. 제한 판정, 자격 증명 확인, 이벤트 기록 또는 초기화를 한 잠금 구간에서 한다.
     * 호출자는 입력 형식 검증(이메일 형식 등)을 먼저 끝내고, 정규화하지 않은 이메일을 넘겨도 된다.
     * credentialsValid는 비밀번호가 맞으면 true를 돌려준다. 제한 상태에서는 호출하지 않는다.
     */
    public LoginAttemptResult attemptLogin(String rawEmail, String clientIp, BooleanSupplier credentialsValid) {
        if (credentialsValid == null) {
            throw new IllegalArgumentException("credentialsValid는 필수입니다.");
        }
        String email = Member.normalizeEmail(rawEmail);
        String pairHash = hasher.hmac(email + "\n" + clientIp);
        String ipHash = hasher.hmac(clientIp);
        List<String> counters = sortedKeys(
                hasher.counterKey(LOGIN_PAIR_PREFIX, pairHash),
                hasher.counterKey(LOGIN_IP_PREFIX, ipHash));

        LoginAttemptResult result = inTransaction(() -> {
            repository.lockCounters(counters);
            LocalDateTime now = LocalDateTime.now(clock);
            RateLimitDecision pair = LOGIN_PAIR.evaluate(
                    repository.recentLoginFailuresByPair(pairHash, LOGIN_PAIR.since(now), LOGIN_PAIR.limit()), now);
            RateLimitDecision ip = LOGIN_IP.evaluate(
                    repository.recentLoginFailuresByIp(ipHash, LOGIN_IP.since(now), LOGIN_IP.limit()), now);
            Optional<Long> retryAfter = longestRetry(pair, ip);
            if (retryAfter.isPresent()) {
                return new LoginAttemptResult.Limited(retryAfter.get());
            }
            if (credentialsValid.getAsBoolean()) {
                repository.deleteLoginFailuresByPair(pairHash);
                return new LoginAttemptResult.Authenticated();
            }
            repository.insertLoginFailure(pairHash, ipHash, now);
            return new LoginAttemptResult.InvalidCredentials();
        });
        purgeAfterRequest(EventTable.LOGIN_FAILURE);
        return result;
    }

    /**
     * 회원가입 요청 1회를 집계한다. JSON 파싱과 DTO 검증보다 먼저 호출한다.
     * 잘못된 입력도 집계한다. 제한을 넘은 요청은 이벤트를 추가하지 않는다.
     * 화면, API, Google 신규 가입 닉네임 제출이 같은 IP 카운터를 쓴다.
     */
    public RateLimitDecision recordSignupRequest(String clientIp) {
        String ipHash = hasher.hmac(clientIp);
        String counter = hasher.counterKey(SIGNUP_IP_PREFIX, ipHash);
        RateLimitDecision decision = inTransaction(() -> {
            repository.lockCounters(List.of(counter));
            LocalDateTime now = LocalDateTime.now(clock);
            RateLimitDecision result = SIGNUP_IP.evaluate(
                    repository.recentSignupRequests(ipHash, SIGNUP_IP.since(now), SIGNUP_IP.limit()), now);
            if (result instanceof RateLimitDecision.Allowed) {
                repository.insertSignupRequest(ipHash, now);
            }
            return result;
        });
        purgeAfterRequest(EventTable.SIGNUP_REQUEST);
        return decision;
    }

    /**
     * Google OAuth2 요청 1회를 집계한다. 로그인 시작, 계정 연동 시작 POST, 콜백이 모두 이 메서드를 호출한다.
     * 한 사용자 요청은 한 번만 호출한다. 서버 내부 리다이렉트로 다시 호출되는 경우는 호출하지 않는다 (API_SPEC 1.4).
     */
    public RateLimitDecision recordGoogleOAuthRequest(String clientIp) {
        String ipHash = hasher.hmac(clientIp);
        String counter = hasher.counterKey(GOOGLE_OAUTH_IP_PREFIX, ipHash);
        RateLimitDecision decision = inTransaction(() -> {
            repository.lockCounters(List.of(counter));
            LocalDateTime now = LocalDateTime.now(clock);
            RateLimitDecision result = GOOGLE_OAUTH_IP.evaluate(
                    repository.recentGoogleOAuthRequests(ipHash, GOOGLE_OAUTH_IP.since(now), GOOGLE_OAUTH_IP.limit()), now);
            if (result instanceof RateLimitDecision.Allowed) {
                repository.insertGoogleOAuthRequest(ipHash, now);
            }
            return result;
        });
        purgeAfterRequest(EventTable.GOOGLE_OAUTH_REQUEST);
        return decision;
    }

    /**
     * Refresh 요청을 계열(family_id) 기준으로 집계한다. RefreshTokenService.rotate()가 회원 행과 토큰 행을 잠근 뒤,
     * 재사용 탐지 다음, 만료 확인 전에 호출한다. 이 호출은 그 회전 트랜잭션에 참여한다.
     * family_id는 HMAC하지 않는다. 계열을 확인할 수 없는 요청은 이 메서드를 호출하지 않는다.
     */
    public RateLimitDecision recordRefreshRequest(String familyId) {
        String counter = hasher.counterKey(REFRESH_FAMILY_PREFIX, familyId);
        RateLimitDecision decision = inTransaction(() -> {
            repository.lockCounters(List.of(counter));
            LocalDateTime now = LocalDateTime.now(clock);
            RateLimitDecision result = REFRESH_FAMILY.evaluate(
                    repository.recentRefreshRequests(familyId, REFRESH_FAMILY.since(now), REFRESH_FAMILY.limit()), now);
            if (result instanceof RateLimitDecision.Allowed) {
                repository.insertRefreshRequest(familyId, now);
            }
            return result;
        });
        purgeAfterRequest(EventTable.REFRESH_REQUEST);
        return decision;
    }

    private <T> T inTransaction(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> work.get());
            } catch (PessimisticLockingFailureException e) {
                // 잠금 대기 시간 초과와 교착 상태가 여기에 해당한다. 트랜잭션은 롤백되어 이벤트는 남지 않았다.
                if (attempt >= MAX_LOCK_ATTEMPTS) {
                    throw new RateLimitUnavailableException();
                }
            }
        }
    }

    /** 요청 시 정리. 실패해도 요청 결과는 바뀌지 않는다. 외부 트랜잭션 안에서는 하지 않는다. */
    private void purgeAfterRequest(EventTable table) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        try {
            cleanup.purgeOneBatch(table, LocalDateTime.now(clock), REQUEST_PURGE_BATCH);
        } catch (RuntimeException e) {
            log.warn("요청 제한 이벤트 요청 시 정리를 건너뜀: 테이블={}, 원인={}",
                    table.tableName(), e.getClass().getSimpleName());
        }
    }

    private static List<String> sortedKeys(String... keys) {
        List<String> list = new ArrayList<>(List.of(keys));
        Collections.sort(list);
        return list;
    }

    private static Optional<Long> longestRetry(RateLimitDecision... decisions) {
        long longest = -1;
        boolean limited = false;
        for (RateLimitDecision decision : decisions) {
            if (decision instanceof RateLimitDecision.Limited limit) {
                limited = true;
                longest = Math.max(longest, limit.retryAfterSeconds());
            }
        }
        return limited ? Optional.of(longest) : Optional.empty();
    }
}
