package com.busanlocalfood.member.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.RefreshToken;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.repository.RefreshTokenRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL 8 테스트 DB에서 Refresh Token 생명주기를 검증한다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 db/manual/001_create_member_refresh_token.sql과 002_add_refresh_token_family_id.sql을 순서대로 적용해야 한다.
 *
 * 이 클래스는 테스트 트랜잭션을 열지 않는다. 서비스의 커밋 결과를 실제로 확인하기 위해서다.
 * 각 테스트가 만든 회원과 토큰 행은 @AfterEach에서 삭제한다. 개발 DB에는 연결하지 않는다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RefreshTokenService.class, RefreshTokenGenerator.class, RefreshTokenConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenLifecycleDbTest {

    private static final String HASH_PW = "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNO";

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdMemberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long memberId : createdMemberIds) {
            jdbcTemplate.update("DELETE FROM refresh_token WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
        createdMemberIds.clear();
    }

    private Member newMember() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member saved = memberRepository.saveAndFlush(
                Member.create("life" + suffix + "@example.com", HASH_PW, "생명" + suffix.substring(0, 4), null));
        createdMemberIds.add(saved.getId());
        return saved;
    }

    private static String rawOf(IssuedRefreshToken issued) {
        return issued.token();
    }

    /** 회원의 모든 행을 id 순서로 읽는다. 커밋된 값을 DB에서 직접 읽는다. */
    private List<Row> rowsOf(long memberId) {
        return jdbcTemplate.query(
                "SELECT id, token_hash, family_id, revoked_at FROM refresh_token WHERE member_id = ? ORDER BY id",
                (rs, n) -> new Row(
                        rs.getLong("id"),
                        rs.getString("token_hash"),
                        rs.getString("family_id"),
                        Optional.ofNullable(rs.getTimestamp("revoked_at")).map(Timestamp::toLocalDateTime)),
                memberId);
    }

    private int activeCount(long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE member_id = ? AND revoked_at IS NULL", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    private record Row(long id, String tokenHash, String familyId, Optional<LocalDateTime> revokedAt) {
    }

    @Test
    void 신규_계열_발급_후_DB에는_해시만_저장되고_family_id는_UUID_v4이다() {
        Member member = newMember();

        IssuedRefreshToken issued = service.issueForNewLogin(member.getId());

        List<Row> rows = rowsOf(member.getId());
        assertThat(rows).hasSize(1);
        Row row = rows.get(0);
        assertThat(row.tokenHash()).isEqualTo(RefreshTokenHasher.sha256Hex(rawOf(issued)));
        assertThat(row.tokenHash()).matches("[0-9a-f]{64}");
        assertThat(UUID.fromString(row.familyId()).version()).isEqualTo(4);
        assertThat(row.revokedAt()).isEmpty();
        // 원문은 어떤 컬럼에도 없어야 한다.
        Integer rawMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_hash = ? OR family_id = ?",
                Integer.class, rawOf(issued), rawOf(issued));
        assertThat(rawMatches).isZero();
    }

    @Test
    void 정상_회전은_이전_행을_폐기하고_새_행은_같은_family_id를_가진다() {
        Member member = newMember();
        IssuedRefreshToken first = service.issueForNewLogin(member.getId());

        RefreshTokenRotation result = service.rotate(rawOf(first));

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rotated.class);
        List<Row> rows = rowsOf(member.getId());
        assertThat(rows).hasSize(2);
        Row old = rows.get(0);
        Row next = rows.get(1);
        assertThat(old.revokedAt()).isPresent();
        assertThat(next.revokedAt()).isEmpty();
        assertThat(next.familyId()).isEqualTo(old.familyId());
        assertThat(next.tokenHash()).isNotEqualTo(old.tokenHash());
        // 기존 행은 삭제하지 않고 해시를 보존한다.
        assertThat(old.tokenHash()).isEqualTo(RefreshTokenHasher.sha256Hex(rawOf(first)));
    }

    @Test
    void 만료된_토큰은_갱신되지_않고_다른_활성_토큰은_유지된다() {
        Member member = newMember();
        IssuedRefreshToken active = service.issueForNewLogin(member.getId());
        // 만료된 행은 서비스 대신 엔티티로 직접 넣는다. 발급 시각을 과거로 되돌릴 수 없기 때문이다.
        String expiredRaw = "expired-raw-token-for-db-test-0000001";
        refreshTokenRepository.saveAndFlush(RefreshToken.issueNewFamily(
                memberRepository.getReferenceById(member.getId()),
                RefreshTokenHasher.sha256Hex(expiredRaw),
                LocalDateTime.now().minusMinutes(1)));

        RefreshTokenRotation result = service.rotate(expiredRaw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rejected.class);
        assertThat(activeCount(member.getId())).isEqualTo(2);
        assertThat(rowsOf(member.getId()).stream().filter(r -> r.revokedAt().isPresent())).isEmpty();
        // 활성 토큰은 그대로 갱신할 수 있어야 한다.
        assertThat(service.rotate(rawOf(active))).isInstanceOf(RefreshTokenRotation.Rotated.class);
    }

    @Test
    void 폐기된_토큰_재사용은_회원의_모든_활성_토큰을_커밋된_상태로_폐기한다() {
        Member member = newMember();
        IssuedRefreshToken a = service.issueForNewLogin(member.getId());
        RefreshTokenRotation rotatedA = service.rotate(rawOf(a));
        String b = ((RefreshTokenRotation.Rotated) rotatedA).next().token();
        assertThat(activeCount(member.getId())).isEqualTo(1);

        // 이미 폐기된 A를 다시 제출한다. 예외가 아니라 결과 값으로 돌아와야 한다.
        RefreshTokenRotation[] holder = new RefreshTokenRotation[1];
        assertThatCode(() -> holder[0] = service.rotate(rawOf(a))).doesNotThrowAnyException();

        assertThat(holder[0]).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(((RefreshTokenRotation.ReuseDetected) holder[0]).revokedCount()).isEqualTo(1);
        // 이 테스트는 트랜잭션 밖에서 실행되므로 아래 조회는 커밋된 값이다. 롤백되었다면 활성 수가 1로 남는다.
        assertThat(activeCount(member.getId())).isZero();
        // 폐기된 행 2개는 모두 보존되고 해시도 그대로다.
        List<Row> rows = rowsOf(member.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.revokedAt().isPresent());
        assertThat(rows.get(0).tokenHash()).isEqualTo(RefreshTokenHasher.sha256Hex(rawOf(a)));
        assertThat(rows.get(1).tokenHash()).isEqualTo(RefreshTokenHasher.sha256Hex(b));

        // 재사용으로 전부 폐기된 뒤 B를 제출해도 같은 규칙으로 처리된다. 활성 토큰이 없으므로 탐지 건수는 0이다.
        RefreshTokenRotation afterWipe = service.rotate(b);
        assertThat(afterWipe).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(((RefreshTokenRotation.ReuseDetected) afterWipe).revokedCount()).isZero();
    }

    @Test
    void 서로_다른_회원의_토큰은_재사용_탐지에서_서로_영향을_주지_않는다() {
        Member x = newMember();
        Member y = newMember();
        IssuedRefreshToken xa = service.issueForNewLogin(x.getId());
        IssuedRefreshToken ya = service.issueForNewLogin(y.getId());
        service.rotate(rawOf(xa));

        // X의 폐기된 토큰을 다시 제출한다.
        assertThat(service.rotate(rawOf(xa))).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);

        assertThat(activeCount(x.getId())).isZero();
        assertThat(activeCount(y.getId())).isEqualTo(1);
        assertThat(service.rotate(rawOf(ya))).isInstanceOf(RefreshTokenRotation.Rotated.class);
    }

    @Test
    void 로그아웃한_토큰은_행을_남기고_재사용하면_재사용으로_탐지된다() {
        Member member = newMember();
        IssuedRefreshToken loggedOut = service.issueForNewLogin(member.getId());
        IssuedRefreshToken otherDevice = service.issueForNewLogin(member.getId());

        service.logout(rawOf(loggedOut));
        service.logout(rawOf(loggedOut)); // 중복 로그아웃도 안전해야 한다.

        List<Row> rows = rowsOf(member.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).revokedAt()).isPresent();
        assertThat(activeCount(member.getId())).isEqualTo(1);

        RefreshTokenRotation result = service.rotate(rawOf(loggedOut));
        assertThat(result).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(((RefreshTokenRotation.ReuseDetected) result).revokedCount()).isEqualTo(1);
        assertThat(activeCount(member.getId())).isZero();
        assertThat(service.rotate(rawOf(otherDevice))).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
    }

    @Test
    void 같은_토큰을_동시에_갱신하면_하나만_회전하고_나머지는_재사용으로_판정되며_활성_토큰은_남지_않는다() throws Exception {
        for (int round = 0; round < 5; round++) {
            Member member = newMember();
            IssuedRefreshToken issued = service.issueForNewLogin(member.getId());
            String raw = rawOf(issued);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Callable<RefreshTokenRotation> task = () -> {
                    start.await();
                    return service.rotate(raw);
                };
                Future<RefreshTokenRotation> first = pool.submit(task);
                Future<RefreshTokenRotation> second = pool.submit(task);
                start.countDown();

                List<String> outcomes = List.of(first.get().getClass().getSimpleName(),
                                second.get().getClass().getSimpleName())
                        .stream().sorted().collect(Collectors.toList());
                assertThat(outcomes).containsExactly("ReuseDetected", "Rotated");
            } finally {
                pool.shutdownNow();
            }
            // 두 요청 모두 끝난 뒤 회원의 활성 토큰은 하나도 없어야 한다. 교차 실행에서도 남는 토큰이 없어야 한다.
            assertThat(activeCount(member.getId())).isZero();
        }
    }

    @Test
    void 신규_발급은_다른_트랜잭션이_잡은_회원_행_잠금을_기다린다() throws Exception {
        Member member = newMember();
        service.issueForNewLogin(member.getId());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 테스트 트랜잭션이 회원 행을 잠근 채 대기한다.
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
                memberRepository.findByIdForUpdate(member.getId());
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<IssuedRefreshToken> issuing = pool.submit(() -> service.issueForNewLogin(member.getId()));

            // 회원 행이 잠긴 동안에는 신규 발급이 끝나면 안 된다.
            // 참고: 잠금 없는 조회라도 INSERT가 FK 때문에 같은 부모 행을 기다리므로 이 검사만으로는 명시적 잠금 호출을 구분하지 못한다.
            // 명시적 잠금 호출은 RefreshTokenServiceTest가 findByIdForUpdate 호출과 findById 미호출로 검증한다.
            assertThatThrownBy(() -> issuing.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThat(activeCount(member.getId())).isEqualTo(1);

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            IssuedRefreshToken issued = issuing.get(5, TimeUnit.SECONDS);

            assertThat(issued.token()).isNotBlank();
            assertThat(activeCount(member.getId())).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 신규_발급과_재사용_탐지가_동시에_실행되어도_직렬화되어_결과가_한_순서와_일치한다() throws Exception {
        for (int round = 0; round < 5; round++) {
            Member member = newMember();
            IssuedRefreshToken old = service.issueForNewLogin(member.getId());
            RefreshTokenRotation rotated = service.rotate(rawOf(old));
            String oldFamily = rowsOf(member.getId()).get(0).familyId();
            // 재사용 대상: 이미 회전되어 폐기된 old 토큰. 회원의 활성 토큰은 회전으로 생긴 B 하나다.
            assertThat(rotated).isInstanceOf(RefreshTokenRotation.Rotated.class);
            assertThat(activeCount(member.getId())).isEqualTo(1);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<IssuedRefreshToken> issuing = pool.submit(() -> {
                    start.await();
                    return service.issueForNewLogin(member.getId());
                });
                Future<RefreshTokenRotation> reuse = pool.submit(() -> {
                    start.await();
                    return service.rotate(rawOf(old));
                });
                start.countDown();

                IssuedRefreshToken fresh = issuing.get(10, TimeUnit.SECONDS);
                RefreshTokenRotation reuseResult = reuse.get(10, TimeUnit.SECONDS);

                // 재사용 탐지는 예외 없이 결과로 돌아와야 한다.
                assertThat(reuseResult).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
                int revoked = ((RefreshTokenRotation.ReuseDetected) reuseResult).revokedCount();

                List<Row> rows = rowsOf(member.getId());
                Row freshRow = rows.stream()
                        .filter(r -> r.tokenHash().equals(RefreshTokenHasher.sha256Hex(rawOf(fresh))))
                        .findFirst().orElseThrow();

                // 신규 발급은 기존 Family ID를 재사용하지 않고 새 UUID v4를 만든다.
                assertThat(freshRow.familyId()).isNotEqualTo(oldFamily);
                assertThat(UUID.fromString(freshRow.familyId()).version()).isEqualTo(4);

                // 두 직렬 순서 중 하나와 일치해야 한다.
                //  (a) 재사용 먼저: B만 폐기(1건), 이후 신규 발급이 활성으로 남는다.
                //  (b) 발급 먼저: 신규 행까지 폐기(2건), 활성 토큰 없음.
                boolean freshActive = freshRow.revokedAt().isEmpty();
                if (freshActive) {
                    assertThat(revoked).isEqualTo(1);
                } else {
                    assertThat(revoked).isEqualTo(2);
                }
                assertThat(activeCount(member.getId())).isEqualTo(freshActive ? 1 : 0);

                // 기존 계열의 행은 모두 폐기되어 있어야 한다. 신규 발급이 옛 계열을 되살리면 안 된다.
                assertThat(rows.stream().filter(r -> r.familyId().equals(oldFamily)))
                        .allMatch(r -> r.revokedAt().isPresent());
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
