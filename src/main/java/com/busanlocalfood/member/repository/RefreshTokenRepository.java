package com.busanlocalfood.member.repository;

import com.busanlocalfood.member.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 요청 쿠키 원문의 SHA-256 해시로 조회한다. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 회원의 폐기되지 않은 Refresh Token 목록 (재사용 탐지 시 일괄 폐기 대상). */
    List<RefreshToken> findAllByMemberIdAndRevokedAtIsNull(Long memberId);
}
