package com.busanlocalfood.member.repository;

import com.busanlocalfood.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 이메일은 소문자로 정규화한 값으로 조회한다. */
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByGoogleSub(String googleSub);
}
