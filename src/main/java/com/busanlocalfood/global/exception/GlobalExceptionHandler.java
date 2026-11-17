package com.busanlocalfood.global.exception;

import com.busanlocalfood.global.response.ApiErrorResponse;
import com.busanlocalfood.member.ratelimit.RateLimitUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BusinessException을 docs/API_SPEC.md 1.1절의 실패 응답 형식으로 변환한다.
 * TODO(인증·API 구현 단계): 검증 실패와 예상하지 못한 예외의 공통 처리를 추가한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiErrorResponse.of(errorCode));
    }

    /** 요청 제한 초과: 429 RATE_LIMITED와 재시도까지 남은 초(Retry-After). 쿠키는 건드리지 않는다. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(RateLimitExceededException e) {
        return ResponseEntity.status(ErrorCode.RATE_LIMITED.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ApiErrorResponse.of(ErrorCode.RATE_LIMITED));
    }

    /**
     * 요청 제한 판정을 잠금 재시도 후에도 끝내지 못했다. 요청을 허용하지 않는다 (fail-closed).
     * 제한 초과가 아니므로 429가 아니라 일반 내부 오류로 응답하며, 세부 원인은 노출하지 않는다.
     */
    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitUnavailable(RateLimitUnavailableException e) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * 본문 JSON 오류: 파싱 실패, 본문 누락, 알 수 없는 필드, 타입 불일치.
     * 세부 원인은 노출하지 않고 공통 400 VALIDATION_FAILED로 응답한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED));
    }
}
