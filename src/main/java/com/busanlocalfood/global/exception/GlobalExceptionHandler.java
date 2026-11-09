package com.busanlocalfood.global.exception;

import com.busanlocalfood.global.response.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
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
}
