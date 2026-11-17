package com.busanlocalfood.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드. code 값은 docs/API_SPEC.md 1.3절의 code와 같아야 한다.
 */
public enum ErrorCode {

    // 공통 입력 오류: 잘못된 JSON, 알 수 없는 필드, 지원하지 않는 Content-Type (API_SPEC 1.3)
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),

    // 회원가입·로그인 검증 (FUNCTION_SPEC F1-01, API_SPEC 1.3)
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "이메일을 입력해 주세요."),
    EMAIL_INVALID(HttpStatus.BAD_REQUEST, "올바른 이메일 형식이 아닙니다. 255자 이하로 입력해 주세요."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "닉네임을 입력해 주세요."),
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 한글, 영문, 숫자로 2~20자 입력해 주세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "비밀번호를 입력해 주세요."),
    PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "비밀번호 조건을 확인해 주세요. 8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가, 확인 값 일치."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 인증 (API_SPEC 1.3). 토큰이 없거나 만료, 폐기, 위조된 경우 모두 같은 메시지를 쓴다. 원인은 노출하지 않는다.
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인이 필요하거나 인증 정보가 올바르지 않습니다. 다시 로그인해 주세요."),

    // 요청 제한 (API_SPEC 1.3, 1.4). 메시지에 제한 기준이나 식별 정보를 넣지 않는다.
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

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
