package com.busanlocalfood.member.refresh;

import com.busanlocalfood.member.ratelimit.RateLimitDecision;

/**
 * 회전 직전의 계열별 요청 제한 판정 지점 (API_SPEC 2.3, 1.4).
 * RefreshTokenService.rotate()가 회원 행과 토큰 행을 잠근 상태에서, 재사용 탐지 뒤 만료 확인 전에 호출한다.
 * 구현은 같은 트랜잭션 안에서 판정하고, 통과한 요청만 이벤트로 기록한다. 외부 네트워크 호출을 하지 않는다.
 * familyId는 가명 식별자이므로 구현에서도 로그에 남기지 않는다.
 * 구현체는 RefreshTokenRateLimitGate 하나다. 다른 구현을 추가하면 빈이 둘이 되므로 기동이 실패한다.
 */
public interface RefreshRotationGate {

    RateLimitDecision check(String familyId);
}
