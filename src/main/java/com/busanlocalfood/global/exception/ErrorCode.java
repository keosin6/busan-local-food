package com.busanlocalfood.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드. code 값은 docs/API_SPEC.md 1.3절의 code와 같아야 한다.
 */
public enum ErrorCode {

    // 공통 입력 오류: 잘못된 JSON, 알 수 없는 필드, 지원하지 않는 Content-Type (API_SPEC 1.3)
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),

    // Google 계정 연결 (API_SPEC 1.3). 이미 다른 회원에게 연결된 Google 계정이다.
    GOOGLE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 회원에게 연결된 Google 계정입니다."),

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

    // 맛집 (API_SPEC 1.3, 3.1, 4.x). 권한과 존재 여부 오류는 작성자 확인 뒤 서비스 계층에서 낸다.
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."),
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "맛집을 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 할 권한이 없습니다."),

    // 리뷰 (API_SPEC 1.3, 6.x, FUNCTION_SPEC F1-04). 한 회원은 한 맛집에 리뷰를 하나만 쓴다.
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 이 맛집에 리뷰를 작성했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),

    // 찜 (API_SPEC 1.3, 8.x, FUNCTION_SPEC F1-06). 한 회원은 한 맛집을 한 번만 찜할 수 있다.
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 찜한 맛집입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "찜한 맛집이 아닙니다."),

    // AI 맛집 추천 (API_SPEC 9.1, FUNCTION_SPEC F1-07). 메시지에 키, 모델 ID, 취향 원문, AI 응답 원문을 넣지 않는다.
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 추천을 지금 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 추천 결과를 처리하지 못했습니다. 다시 시도해 주세요."),

    // 맛집 이미지 (API_SPEC 1.3, 5.1, FUNCTION_SPEC F1-03). 메시지에 저장소 자격 증명이나 서명 정보를 넣지 않는다.
    IMAGE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "JPEG, PNG, WEBP 형식의 이미지만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하만 올릴 수 있습니다."),
    IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "맛집 이미지는 최대 5장까지 등록할 수 있습니다."),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "이미지 저장소 요청이 실패했습니다. 잠시 후 다시 시도해 주세요."),
    STORAGE_RETRY_RUNNING(HttpStatus.CONFLICT, "S3 삭제 재시도가 이미 진행 중입니다. 끝난 뒤 다시 시도해 주세요."),

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
