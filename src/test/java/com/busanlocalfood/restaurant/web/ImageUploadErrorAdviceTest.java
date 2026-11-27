package com.busanlocalfood.restaurant.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 요청 크기 초과 응답의 경로별 분기. 화면은 수정 화면으로, API는 JSON으로 돌려준다. */
class ImageUploadErrorAdviceTest {

    private final ImageUploadErrorAdvice advice = new ImageUploadErrorAdvice();
    private final MaxUploadSizeExceededException tooLarge = new MaxUploadSizeExceededException(5L * 1024 * 1024);

    @Test
    void 화면_이미지_업로드가_크기를_넘으면_수정_화면으로_돌려보낸다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/restaurants/10/images");

        Object result = advice.handleUploadTooLarge(tooLarge, request);

        assertThat(result).isEqualTo("redirect:/restaurants/10/edit?imageError=size");
    }

    @Test
    void API_업로드가_크기를_넘으면_400_IMAGE_TOO_LARGE_JSON을_돌려준다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/restaurants/10/images");

        Object result = advice.handleUploadTooLarge(tooLarge, request);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains(ErrorCode.IMAGE_TOO_LARGE.name());
    }

    @Test
    void 다른_경로에서_크기를_넘으면_맛집_목록으로_돌려보낸다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/restaurants/abc/images");

        assertThat(advice.handleUploadTooLarge(tooLarge, request)).isEqualTo("redirect:/restaurants");
    }
}
