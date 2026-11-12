package com.busanlocalfood.member.repository;

import com.busanlocalfood.member.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 잠금 전 사전 조회: 해시로 회원 ID만 읽는다. 행을 잠그지 않으며, Entity도 영속성 컨텍스트에 올리지 않는다.
     * 이 결과로 정상 회전 여부를 판단하지 않는다. 회원 행을 잠근 뒤 반드시 findByTokenHashForUpdate로 다시 읽는다.
     */
    @Query("select rt.member.id from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<Long> findMemberIdByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 잠금 조회 (SELECT ... FOR UPDATE). 잠금 읽기는 최신 커밋 값을 읽으므로 REPEATABLE READ의 스냅샷에 가려지지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 회원의 활성 토큰을 ID 오름차순으로 잠근다. 여러 행을 잠글 때 순서를 고정해 교착을 피한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.member.id = :memberId and rt.revokedAt is null order by rt.id")
    List<RefreshToken> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);
}
