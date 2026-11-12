package com.busanlocalfood.member.refresh;

import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import org.springframework.stereotype.Component;

/** RefreshRotationGate의 실제 구현. 계열별 1분 10회 제한을 AuthRateLimitService로 판정한다. */
@Component
public class RefreshTokenRateLimitGate implements RefreshRotationGate {

    private final AuthRateLimitService rateLimitService;

    public RefreshTokenRateLimitGate(AuthRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public RateLimitDecision check(String familyId) {
        return rateLimitService.recordRefreshRequest(familyId);
    }
}
