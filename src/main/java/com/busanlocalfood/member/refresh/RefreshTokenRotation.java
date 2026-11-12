package com.busanlocalfood.member.refresh;

import com.busanlocalfood.member.domain.Role;

/**
 * Refresh Token 회전 처리 결과. 예외 대신 결과 값으로 구분한다.
 * 트랜잭션은 이 값을 반환하는 시점에 커밋된다. 예외를 던지면 재사용 탐지의 전체 폐기가 롤백되므로 쓰지 않는다.
 * 호출자는 커밋 후 결과에 따라 응답한다 (Rotated: 새 쿠키, Rejected·ReuseDetected: 401 TOKEN_INVALID, RateLimited: 429).
 */
public sealed interface RefreshTokenRotation
        permits RefreshTokenRotation.Rotated, RefreshTokenRotation.Rejected, RefreshTokenRotation.ReuseDetected,
        RefreshTokenRotation.RateLimited {

    /** 정상 회전. 같은 계열의 새 토큰을 발급했다. */
    record Rotated(long memberId, Role role, IssuedRefreshToken next) implements RefreshTokenRotation {
    }

    /** 거부: 알 수 없는 토큰, 만료된 토큰. 아무 행도 바꾸지 않는다 (만료 시 다른 활성 토큰도 유지). */
    record Rejected() implements RefreshTokenRotation {
    }

    /** 계열별 요청 제한 초과 (429 RATE_LIMITED). 토큰과 쿠키를 바꾸지 않는다. 재사용 탐지보다 뒤에 판정된다. */
    record RateLimited(long retryAfterSeconds) implements RefreshTokenRotation {
    }

    /** 재사용 탐지: 폐기된 토큰이 다시 제출되었다. 회원의 활성 토큰 revokedCount건을 커밋과 함께 폐기했다. */
    record ReuseDetected(long memberId, int revokedCount) implements RefreshTokenRotation {
    }
}
