package com.busanlocalfood.restaurant.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.member.web.ScreenSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 이미지 화면 제출 (FUNCTION_SPEC F1-03). 수정 화면에서 올리고 지운다. 화면 세션과 CSRF를 쓴다.
 * 작성자 확인은 서비스가 하고, 실패는 수정 화면으로 되돌려 안내 문구를 보인다.
 */
@Controller
public class RestaurantImageScreenController {

    private final RestaurantImageService imageService;

    public RestaurantImageScreenController(RestaurantImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/restaurants/{id}/images")
    public String upload(@PathVariable("id") long id,
                         @RequestParam(name = "file", required = false) MultipartFile file) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            imageService.upload(id, memberId, file);
            return "redirect:/restaurants/" + id + "/edit?image=added";
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN || e.getErrorCode() == ErrorCode.RESTAURANT_NOT_FOUND) {
                return RestaurantScreenController.redirectAfterFailure(e, id);
            }
            return "redirect:/restaurants/" + id + "/edit?imageError=" + uploadErrorKey(e.getErrorCode());
        }
    }

    @PostMapping("/restaurants/{id}/images/{imageId}/delete")
    public String delete(@PathVariable("id") long id, @PathVariable("imageId") long imageId) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            ImageDeleteResult result = imageService.delete(id, imageId, memberId);
            String storage = result.storageDeleted() ? "" : "&storage=1";
            return "redirect:/restaurants/" + id + "/edit?image=deleted" + storage;
        } catch (BusinessException e) {
            return RestaurantScreenController.redirectAfterFailure(e, id);
        }
    }

    private static String uploadErrorKey(ErrorCode code) {
        return switch (code) {
            case IMAGE_TYPE_NOT_ALLOWED -> "type";
            case IMAGE_TOO_LARGE -> "size";
            case IMAGE_LIMIT_EXCEEDED -> "limit";
            case STORAGE_ERROR -> "storage";
            case VALIDATION_FAILED -> "file";
            default -> "failed";
        };
    }
}
