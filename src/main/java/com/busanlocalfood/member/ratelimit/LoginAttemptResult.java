package com.busanlocalfood.member.ratelimit;

/**
 * 로그인 시도 결과. 제한 판정과 자격 증명 검증을 같은 잠금 구간에서 수행한 결과다.
 * Limited는 자격 증명을 확인하지 않은 상태이며, 실패 횟수를 늘리지 않는다.
 */
public sealed interface LoginAttemptResult
        permits LoginAttemptResult.Authenticated, LoginAttemptResult.InvalidCredentials, LoginAttemptResult.Limited {

    /** 자격 증명이 맞다. 이메일+IP 조합의 실패 기록을 초기화했다. */
    record Authenticated() implements LoginAttemptResult {
    }

    /** 자격 증명이 틀렸다. 실패 이벤트 1건을 기록했다. 응답은 INVALID_CREDENTIALS로 같게 한다. */
    record InvalidCredentials() implements LoginAttemptResult {
    }

    /** 제한 초과. 자격 증명을 확인하지 않았고 이벤트를 추가하지 않았다. 429 RATE_LIMITED로 응답한다. */
    record Limited(long retryAfterSeconds) implements LoginAttemptResult {
    }
}
