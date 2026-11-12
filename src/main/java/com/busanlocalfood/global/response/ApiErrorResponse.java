package com.busanlocalfood.global.response;

import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 실패 응답 형식. docs/API_SPEC.md 1.1절과 같다.
 */
public record ApiErrorResponse(boolean success, ErrorBody error, LocalDateTime timestamp) {

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(
                false,
                new ErrorBody(errorCode.name(), errorCode.getMessage()),
                LocalDateTime.now(TimeConfig.SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    public record ErrorBody(String code, String message) {
    }
}
