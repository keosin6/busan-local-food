package com.busanlocalfood.member.repository;

import com.busanlocalfood.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 회원 행 잠금 (SELECT ... FOR UPDATE). Refresh Token 상태 변경 경로의 첫 번째 잠금이다 (T14 A안).
     * 같은 회원의 Refresh Token 변경은 이 잠금으로 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    /** 이메일은 소문자로 정규화한 값으로 조회한다. */
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByGoogleSub(String googleSub);
}
