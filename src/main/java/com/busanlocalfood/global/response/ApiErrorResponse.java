package com.busanlocalfood.global.response;

import com.busanlocalfood.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 실패 응답 형식. docs/API_SPEC.md 1.1절과 같다.
 */
public record ApiErrorResponse(boolean success, ErrorBody error, LocalDateTime timestamp) {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(
                false,
                new ErrorBody(errorCode.name(), errorCode.getMessage()),
                LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    public record ErrorBody(String code, String message) {
    }
}
