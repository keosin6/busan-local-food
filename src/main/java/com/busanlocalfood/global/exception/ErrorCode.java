package com.busanlocalfood.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드. code 값은 docs/API_SPEC.md 1.3절의 code와 같아야 한다.
 */
public enum ErrorCode {

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
