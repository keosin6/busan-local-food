package com.busanlocalfood.restaurant.web;

import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 이미지 API (API_SPEC 5.1, 5.2). 인증은 JWT만 쓴다. 작성자 확인은 RestaurantImageService가 한다.
 * API 체인은 CSRF 면제 대상이 아니면 Bearer 요청만 받는다. 이 컨트롤러는 화면 세션을 보지 않는다.
 */
@RestController
public class RestaurantImageApiController {

    private final RestaurantImageService imageService;

    public RestaurantImageApiController(RestaurantImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/api/restaurants/{id}/images")
    public ResponseEntity<ApiSuccessResponse<ImageSummary>> upload(
            @PathVariable("id") long id,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication) {
        long memberId = RestaurantApiController.requireMemberId(authentication);
        ImageSummary created = imageService.upload(id, memberId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(created));
    }

    @DeleteMapping("/api/restaurants/{id}/images/{imageId}")
    public ApiSuccessResponse<ImageDeleteResult> delete(@PathVariable("id") long id,
                                                        @PathVariable("imageId") long imageId,
                                                        Authentication authentication) {
        long memberId = RestaurantApiController.requireMemberId(authentication);
        return ApiSuccessResponse.of(imageService.delete(id, imageId, memberId));
    }
}
