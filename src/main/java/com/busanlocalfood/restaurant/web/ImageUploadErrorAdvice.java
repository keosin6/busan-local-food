package com.busanlocalfood.restaurant.web;

import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 요청 전체 크기 제한을 넘은 업로드 처리 (FUNCTION_SPEC F1-03, 5MB 제한).
 * Spring은 멀티파트 파싱 단계에서 이 예외를 던지므로 컨트롤러가 잡을 수 없다. 그래서 여기서 경로별로 응답을 나눈다.
 * - API(/api/...): 공통 JSON 오류 응답, 400 IMAGE_TOO_LARGE.
 * - 화면 이미지 업로드: 수정 화면으로 돌려보내고 안내 문구를 보인다.
 */
@ControllerAdvice
public class ImageUploadErrorAdvice {

    private static final Pattern SCREEN_UPLOAD_PATH = Pattern.compile("^/restaurants/(\\d+)/images$");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            return ResponseEntity.status(ErrorCode.IMAGE_TOO_LARGE.getStatus())
                    .body(ApiErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE));
        }
        Matcher matcher = SCREEN_UPLOAD_PATH.matcher(uri);
        if (matcher.matches()) {
            return "redirect:/restaurants/" + matcher.group(1) + "/edit?imageError=size";
        }
        return "redirect:/restaurants";
    }
}
