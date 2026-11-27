package com.busanlocalfood.review.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.service.ReviewService;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 리뷰 화면 제출 (FUNCTION_SPEC F1-04). 맛집 상세 화면의 작성 폼과 본인 리뷰의 삭제 버튼이 쓴다.
 * 화면 세션과 CSRF를 쓴다. 결과는 맛집 상세 화면으로 돌아가 안내 문구로 보여 준다.
 */
@Controller
public class ReviewScreenController {

    private final ReviewService reviewService;

    public ReviewScreenController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/restaurants/{id}/reviews")
    public String create(@PathVariable("id") long id,
                         @RequestParam(name = "rating", required = false) String rating,
                         @RequestParam(name = "content", required = false) String content) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            reviewService.create(id, memberId, new ReviewRequest(parseRating(rating), content));
            return "redirect:/restaurants/" + id + "?review=added#reviews";
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case RESTAURANT_NOT_FOUND -> "redirect:/restaurants?error=not_found";
                case REVIEW_ALREADY_EXISTS -> "redirect:/restaurants/" + id + "?reviewError=duplicate#reviews";
                case VALIDATION_FAILED -> "redirect:/restaurants/" + id + "?reviewError=invalid#reviews";
                case TOKEN_INVALID -> "redirect:/login";
                default -> throw e;
            };
        }
    }

    @PostMapping("/reviews/{reviewId}/delete")
    public String delete(@PathVariable("reviewId") long reviewId,
                         @RequestParam(name = "restaurantId") long restaurantId) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            reviewService.delete(reviewId, memberId);
            return "redirect:/restaurants/" + restaurantId + "?review=deleted#reviews";
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case REVIEW_NOT_FOUND -> "redirect:/restaurants/" + restaurantId + "?reviewError=not_found#reviews";
                case FORBIDDEN -> "redirect:/restaurants/" + restaurantId + "?reviewError=forbidden#reviews";
                default -> throw e;
            };
        }
    }

    /** 화면 입력의 평점 문자열. 비어 있거나 정수가 아니면 null이 되어 VALIDATION_FAILED로 처리된다. */
    private static BigDecimal parseRating(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
