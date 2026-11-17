package com.busanlocalfood.member.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.RefreshToken;
import com.busanlocalfood.member.ratelimit.LockRetryTemplate;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import org.springframework.transaction.PlatformTransactionManager;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** 서비스 판정 로직과 잠금 호출 순서를 Mockito로 검증한다. DB 잠금의 실제 동작은 DB 통합 테스트에서 확인한다. */
class RefreshTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final ZoneId ZONE = com.busanlocalfood.global.config.TimeConfig.SERVICE_ZONE;
    private static final String FAMILY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final long MEMBER_ID = 7L;

    private MemberRepository memberRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenService service;
    private boolean gateOpen;
    private int gateCalls;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new RefreshTokenService(memberRepository, refreshTokenRepository,
                new RefreshTokenGenerator(), new RefreshTokenProperties(1209600),
                familyId -> {
                    gateCalls++;
                    return gateOpen ? new RateLimitDecision.Allowed() : new RateLimitDecision.Limited(60);
                }, new LockRetryTemplate(mock(PlatformTransactionManager.class)), clock);
        gateOpen = true;
        gateCalls = 0;
        member = Member.create("a@example.com", "$2a$10$hash", "부산먹보", null);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    }

    private RefreshToken row(String raw, String familyId, LocalDateTime expiresAt) {
        return RefreshToken.issueInFamily(member, familyId, RefreshTokenHasher.sha256Hex(raw), expiresAt);
    }

    @Test
    void 신규_로그인은_새_계열로_발급하고_해시만_저장한다() {
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        IssuedRefreshToken issued = service.issueForNewLogin(MEMBER_ID);

        // 신규 발급도 회원 행 잠금으로 시작해야 한다. 잠금 없는 조회는 쓰지 않는다.
        verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        verify(memberRepository, never()).findById(anyLong());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken row = saved.getValue();
        assertThat(row.getTokenHash()).isEqualTo(RefreshTokenHasher.sha256Hex(issued.token()));
        assertThat(row.getTokenHash()).isNotEqualTo(issued.token());
        assertThat(java.util.UUID.fromString(row.getFamilyId()).version()).isEqualTo(4);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusDays(14));
        assertThat(row.getExpiresAt()).isEqualTo(NOW.plusDays(14));
    }

    @Test
    void 정상_회전은_기존_행을_폐기하고_같은_family_id로_새_토큰을_발급한다() {
        String oldRaw = "old-token-value-for-rotation-test-0001";
        RefreshToken old = row(oldRaw, FAMILY, NOW.plusDays(1));
        String hash = RefreshTokenHasher.sha256Hex(oldRaw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(old));

        RefreshTokenRotation result = service.rotate(oldRaw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rotated.class);
        RefreshTokenRotation.Rotated rotated = (RefreshTokenRotation.Rotated) result;
        assertThat(rotated.memberId()).isEqualTo(MEMBER_ID);
        assertThat(rotated.next().token()).isNotEqualTo(oldRaw);
        assertThat(old.isRevoked()).isTrue();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(hash);
    }

    @Test
    void 만료된_토큰은_갱신을_거부하고_다른_활성_토큰을_건드리지_않는다() {
        String raw = "expired-token-value-for-test-0000001";
        RefreshToken expired = row(raw, FAMILY, NOW.minusSeconds(1));
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(expired));

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rejected.class);
        assertThat(expired.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).findActiveByMemberIdForUpdate(anyLong());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 알_수_없는_토큰은_거부하고_잠금을_잡지_않는다() {
        when(refreshTokenRepository.findMemberIdByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshTokenRotation result = service.rotate("unknown-token-value-for-test-000000001");

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rejected.class);
        verify(memberRepository, never()).findByIdForUpdate(anyLong());
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    void 폐기된_토큰_재사용은_예외_없이_전체_활성_토큰_폐기_결과를_반환한다() {
        String raw = "reused-token-value-for-test-00000001";
        RefreshToken reused = row(raw, FAMILY, NOW.plusDays(1));
        reused.revoke(NOW.minusMinutes(5));
        RefreshToken activeA = row("active-a-value-for-test-000000000001", FAMILY, NOW.plusDays(1));
        RefreshToken activeB = row("active-b-value-for-test-000000000001", FAMILY, NOW.plusDays(1));
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findActiveByMemberIdForUpdate(MEMBER_ID)).thenReturn(List.of(activeA, activeB));

        RefreshTokenRotation[] holder = new RefreshTokenRotation[1];
        assertThatCode(() -> holder[0] = service.rotate(raw)).doesNotThrowAnyException();

        assertThat(holder[0]).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(((RefreshTokenRotation.ReuseDetected) holder[0]).revokedCount()).isEqualTo(2);
        assertThat(activeA.isRevoked()).isTrue();
        assertThat(activeB.isRevoked()).isTrue();
        // 재사용 탐지 시 새 행은 만들지 않는다. 폐기된 행도 삭제하지 않는다.
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void 회원_행_잠금이_토큰_행_잠금보다_먼저_일어난다() {
        String raw = "order-token-value-for-test-000000000001";
        RefreshToken current = row(raw, FAMILY, NOW.plusDays(1));
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(current));

        service.rotate(raw);

        InOrder order = inOrder(memberRepository, refreshTokenRepository);
        order.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
        order.verify(refreshTokenRepository).findByTokenHashForUpdate(hash);
    }

    @Test
    void 로그아웃은_토큰을_폐기하고_같은_토큰을_다시_처리해도_안전하다() {
        String raw = "logout-token-value-for-test-0000000001";
        RefreshToken token = row(raw, FAMILY, NOW.plusDays(1));
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));

        service.logout(raw);
        LocalDateTime firstRevokedAt = token.getRevokedAt();
        assertThat(firstRevokedAt).isEqualTo(NOW);

        service.logout(raw);
        assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void 로그아웃_알_수_없는_토큰과_빈_값은_아무_잠금도_잡지_않는다() {
        when(refreshTokenRepository.findMemberIdByTokenHash(anyString())).thenReturn(Optional.empty());

        service.logout("unknown-logout-token-value-0000000001");
        service.logout(" ");

        verify(memberRepository, never()).findByIdForUpdate(anyLong());
    }

    private RefreshToken stubLockedRow(String raw, LocalDateTime expiresAt) {
        RefreshToken token = row(raw, FAMILY, expiresAt);
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokenRepository.findMemberIdByTokenHash(hash)).thenReturn(Optional.of(MEMBER_ID));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void 요청_제한에_걸리면_토큰을_바꾸지_않고_RateLimited를_반환한다() {
        String raw = "rate-limited-token-value-for-test-0001";
        RefreshToken token = stubLockedRow(raw, NOW.plusDays(1));
        gateOpen = false;

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.RateLimited.class);
        // 게이트가 준 재시도 초가 그대로 결과에 실려야 HTTP 계층이 Retry-After를 만들 수 있다.
        assertThat(((RefreshTokenRotation.RateLimited) result).retryAfterSeconds()).isEqualTo(60);
        assertThat(token.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 재사용_탐지는_요청_제한보다_먼저_처리되어_제한_판정을_거치지_않는다() {
        String raw = "reuse-before-gate-token-for-test-00001";
        RefreshToken token = stubLockedRow(raw, NOW.plusDays(1));
        token.revoke(NOW.minusMinutes(1));
        when(refreshTokenRepository.findActiveByMemberIdForUpdate(MEMBER_ID)).thenReturn(List.of());
        gateOpen = false;

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.ReuseDetected.class);
        assertThat(gateCalls).isZero();
    }

    @Test
    void 요청_제한_판정은_만료_확인보다_먼저_한다() {
        String raw = "expired-after-gate-token-for-test-0001";
        stubLockedRow(raw, NOW.minusSeconds(1));

        RefreshTokenRotation result = service.rotate(raw);

        assertThat(result).isInstanceOf(RefreshTokenRotation.Rejected.class);
        assertThat(gateCalls).isEqualTo(1);
    }

    @Test
    void 알_수_없는_토큰은_요청_제한_판정을_거치지_않는다() {
        when(refreshTokenRepository.findMemberIdByTokenHash(anyString())).thenReturn(Optional.empty());

        service.rotate("unknown-token-no-gate-for-test-00000001");

        assertThat(gateCalls).isZero();
    }
}
