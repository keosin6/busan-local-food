package com.busanlocalfood.global.exception;

/**
 * 도메인 예외의 공통 상위 타입. ErrorCode에 따라 응답 상태와 메시지가 정해진다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
