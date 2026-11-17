package com.busanlocalfood.global.config;

import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * API 체인의 인증 실패 응답. 401 TOKEN_INVALID를 공통 오류 응답 형식으로 쓴다 (API_SPEC 1.1, 1.3).
 * 실패 원인과 예외 메시지는 응답에 넣지 않는다. 쿠키는 바꾸지 않는다.
 */
public final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setStatus(ErrorCode.TOKEN_INVALID.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(ErrorCode.TOKEN_INVALID));
    }
}
