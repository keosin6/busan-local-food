package com.busanlocalfood.global.config;

import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * API 체인의 권한 부족 응답 (FUNCTION_SPEC F1-09). 로그인은 했지만 권한이 없는 요청에 403 FORBIDDEN을 공통 오류 형식으로 준다.
 * 예외 메시지와 필요한 권한 이름은 응답에 넣지 않는다.
 */
public final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(ErrorCode.FORBIDDEN));
    }
}
