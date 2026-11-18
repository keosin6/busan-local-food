package com.busanlocalfood.global.response;

import java.time.LocalDateTime;

/**
 * 성공 응답 형식 (API_SPEC 1.2). data가 null이면 "data": null로 응답한다 (로그아웃 등).
 */
public record ApiSuccessResponse<T>(boolean success, T data, LocalDateTime timestamp) {

    public static <T> ApiSuccessResponse<T> of(T data) {
        return new ApiSuccessResponse<>(true, data, LocalDateTime.now());
    }
}
