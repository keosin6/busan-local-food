package com.busanlocalfood.member.ratelimit;

/**
 * 잠금 대기 또는 교착 상태가 재시도 후에도 풀리지 않을 때 던진다. 실패 시 닫힘(fail-closed) 원칙을 따른다.
 * 요청을 허용하지 않으며, 메시지에는 식별 정보를 넣지 않는다.
 */
public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException() {
        super("요청 제한 처리를 일시적으로 완료하지 못했습니다.");
    }
}
