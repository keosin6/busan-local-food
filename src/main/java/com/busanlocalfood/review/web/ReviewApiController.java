package com.busanlocalfood.review.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewDeleteResult;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewItem;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 API (API_SPEC 6.1~6.3). 목록은 공개, 작성과 삭제는 JWT가 필요하다. 인증 정책은 SecurityConfig가 정한다.
 * 작성자 확인과 존재 확인은 서비스가 한다. 응답 형식은 기존 API와 같은 ApiSuccessResponse를 쓴다.
 */
@RestController
public class ReviewApiController {

    private final ReviewService reviewService;

    public ReviewApiController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/restaurants/{id}/reviews")
    public ApiSuccessResponse<PageResponse<ReviewItem>> list(
            @PathVariable("id") long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(reviewService.list(id, memberIdOf(authentication), page, size));
    }

    @PostMapping("/api/restaurants/{id}/reviews")
    public ResponseEntity<ApiSuccessResponse<ReviewCreated>> create(
            @PathVariable("id") long id,
            @RequestBody ReviewRequest request,
            Authentication authentication) {
        ReviewCreated created = reviewService.create(id, requireMemberId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(created));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ApiSuccessResponse<ReviewDeleteResult> delete(@PathVariable("reviewId") long reviewId,
                                                         Authentication authentication) {
        return ApiSuccessResponse.of(reviewService.delete(reviewId, requireMemberId(authentication)).result());
    }

    /** JWT 인증 주체의 회원 ID. 인증되지 않았으면 null이다. RestaurantApiController와 같은 규칙이다. */
    static Long memberIdOf(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long memberId) {
            return memberId;
        }
        return null;
    }

    private static long requireMemberId(Authentication authentication) {
        Long memberId = memberIdOf(authentication);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return memberId;
    }
}
