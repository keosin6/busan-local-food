package com.busanlocalfood.member.refresh;

import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.RefreshToken;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 생명주기 서비스: 새 계열 발급, 정상 회전, 재사용 탐지와 전체 폐기, 로그아웃 폐기.
 *
 * 잠금 순서 (DEVELOPMENT_RULES 5.7.4, T14 A안): 회원 행 → Refresh Token 행(ID 오름차순).
 * 잠금 전 사전 조회는 회원 ID 확인용일 뿐이며, 판정은 회원 잠금 뒤의 잠금 재조회 결과로만 한다.
 * 재사용 탐지는 예외를 던지지 않고 결과 값으로 반환하여 전체 폐기가 커밋되도록 한다.
 * 외부 네트워크 호출은 이 트랜잭션 안에서 하지 않는다.
 *
 * 로그인 컨트롤러, Access Token 발급, 쿠키 설정은 이 서비스의 책임이 아니다.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator generator;
    private final RefreshTokenProperties properties;
    private final RefreshRotationGate rotationGate;
    private final Clock clock;

    public RefreshTokenService(MemberRepository memberRepository,
                               RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenGenerator generator,
                               RefreshTokenProperties properties,
                               RefreshRotationGate rotationGate,
                               Clock clock) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.generator = generator;
        this.properties = properties;
        this.rotationGate = rotationGate;
        this.clock = clock;
    }

    /**
     * 새 로그인으로 새 계열을 시작한다. family_id는 UUID v4로 새로 만든다.
     * 화면 로그인, API 전용 로그인, Google 로그인이 공통으로 호출한다.
     *
     * 회원 행을 PESSIMISTIC_WRITE로 잠근 뒤 발급한다. 같은 회원의 재사용 탐지, 회전, 로그아웃과
     * 같은 잠금으로 직렬화되어야 한다 (T14 A안, DEVELOPMENT_RULES 5.7.4). 잠금 순서는 회원 행이 첫 번째다.
     */
    @Transactional
    public IssuedRefreshToken issueForNewLogin(long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now(clock);
        String raw = generator.generate();
        LocalDateTime expiresAt = now.plusSeconds(properties.refreshTokenExpirySeconds());
        refreshTokenRepository.save(
                RefreshToken.issueNewFamily(member, RefreshTokenHasher.sha256Hex(raw), expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    /**
     * 원문 Refresh Token을 제출받아 정상 회전, 거부, 재사용 탐지, 요청 제한 중 하나로 판정한다.
     * 판정 순서: 재사용 탐지 → 요청 제한 → 만료 → 정상 회전 (API_SPEC 2.3).
     * 정상 회전이면 기존 행을 폐기하고 같은 family_id의 새 행을 INSERT한다.
     */
    @Transactional
    public RefreshTokenRotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return new RefreshTokenRotation.Rejected();
        }
        String hash = RefreshTokenHasher.sha256Hex(rawToken);

        // 1) 잠금 없는 사전 조회: 회원 ID만 확인한다. 이 결과로는 판정하지 않는다.
        Optional<Long> memberId = refreshTokenRepository.findMemberIdByTokenHash(hash);
        if (memberId.isEmpty()) {
            return new RefreshTokenRotation.Rejected();
        }

        // 2) 회원 행 잠금 (첫 번째 잠금). 같은 회원의 상태 변경은 여기서 직렬화된다.
        Optional<Member> lockedMember = memberRepository.findByIdForUpdate(memberId.get());
        if (lockedMember.isEmpty()) {
            return new RefreshTokenRotation.Rejected();
        }
        Member member = lockedMember.get();

        // 3) Refresh Token 행 잠금 후 재조회. 잠금 전 결과가 아니라 이 최신 값으로 판정한다.
        Optional<RefreshToken> locked = refreshTokenRepository.findByTokenHashForUpdate(hash);
        if (locked.isEmpty()) {
            return new RefreshTokenRotation.Rejected();
        }
        RefreshToken current = locked.get();
        LocalDateTime now = LocalDateTime.now(clock);

        // 4) 폐기된 토큰의 재제출은 재사용이다. 판정 순서상 만료보다 먼저 본다.
        if (current.isRevoked()) {
            return revokeAllActive(member, now);
        }

        // 5) 계열별 요청 제한. 재사용 탐지 뒤, 만료 확인 전에 판정한다 (API_SPEC 2.3).
        //    제한에 걸리면 토큰을 바꾸지 않는다. 통과한 요청의 이벤트 기록은 gate 구현이 같은 트랜잭션에서 한다.
        if (!rotationGate.tryPass(current.getFamilyId())) {
            return new RefreshTokenRotation.RateLimited();
        }

        // 6) 만료된 토큰은 갱신하지 않는다. 다른 활성 토큰은 건드리지 않는다.
        if (current.isExpiredAt(now)) {
            return new RefreshTokenRotation.Rejected();
        }

        // 7) 정상 회전: 기존 행 폐기, 같은 family_id로 새 행 발급. 기존 행은 삭제하지 않는다.
        current.revoke(now);
        IssuedRefreshToken next = insertInFamily(member, current.getFamilyId(), now);
        return new RefreshTokenRotation.Rotated(member.getId(), member.getRole(), next);
    }

    /**
     * 로그아웃: 원문 토큰의 행을 폐기한다. 회원 행 → 토큰 행 순서로 잠근다.
     * 알 수 없는 토큰, 이미 폐기된 토큰은 아무 일도 하지 않고 끝낸다. 행은 삭제하지 않는다.
     * 로그아웃된 토큰을 나중에 다시 쓰면 재사용으로 판정된다 (DEVELOPMENT_RULES 5.7.4).
     */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = RefreshTokenHasher.sha256Hex(rawToken);
        Optional<Long> memberId = refreshTokenRepository.findMemberIdByTokenHash(hash);
        if (memberId.isEmpty()) {
            return;
        }
        memberRepository.findByIdForUpdate(memberId.get());
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> token.revoke(now));
    }

    /** 재사용 탐지: 같은 트랜잭션에서 회원의 활성 토큰을 ID 순서로 잠가 모두 폐기한다. 예외 없이 결과를 반환한다. */
    private RefreshTokenRotation.ReuseDetected revokeAllActive(Member member, LocalDateTime now) {
        List<RefreshToken> active = refreshTokenRepository.findActiveByMemberIdForUpdate(member.getId());
        active.forEach(token -> token.revoke(now));
        // 로그에는 회원 ID와 건수만 남긴다. 토큰이나 family_id는 남기지 않는다.
        log.warn("Refresh Token 재사용 탐지: memberId={}, 폐기 건수={}", member.getId(), active.size());
        return new RefreshTokenRotation.ReuseDetected(member.getId(), active.size());
    }

    private IssuedRefreshToken insertInFamily(Member member, String familyId, LocalDateTime now) {
        String raw = generator.generate();
        LocalDateTime expiresAt = now.plusSeconds(properties.refreshTokenExpirySeconds());
        refreshTokenRepository.save(
                RefreshToken.issueInFamily(member, familyId, RefreshTokenHasher.sha256Hex(raw), expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
    }
}
