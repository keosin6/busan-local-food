package com.busanlocalfood.member.jwt;

/**
 * Access Token 검증 실패. 메시지는 내부 로그용이며 외부 응답에 그대로 노출하지 않는다.
 * 토큰 원문은 메시지에 포함하지 않는다.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String internalReason) {
        super(internalReason);
    }

    public InvalidAccessTokenException(String internalReason, Throwable cause) {
        super(internalReason, cause);
    }
}
