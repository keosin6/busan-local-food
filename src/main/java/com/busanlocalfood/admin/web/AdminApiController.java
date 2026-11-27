package com.busanlocalfood.admin.web;

import com.busanlocalfood.admin.dto.AdminDtos.AdminMemberItem;
import com.busanlocalfood.admin.dto.AdminDtos.AdminReviewItem;
import com.busanlocalfood.admin.dto.AdminDtos.Dashboard;
import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.admin.dto.AdminDtos.StorageDeletionItem;
import com.busanlocalfood.admin.service.AdminQueryService;
import com.busanlocalfood.admin.service.AdminStorageRetryService;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewDeleteResult;
import com.busanlocalfood.review.service.ReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API (FUNCTION_SPEC F1-09, API_SPEC 11). /api/admin/** 는 SecurityConfig가 ADMIN만 허용한다.
 * 권한은 서비스가 다시 확인한다. 응답 형식은 기존 ApiSuccessResponse와 PageResponse다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminQueryService queries;
    private final AdminStorageRetryService storageRetry;
    private final ReviewService reviews;

    public AdminApiController(AdminQueryService queries, AdminStorageRetryService storageRetry,
                              ReviewService reviews) {
        this.queries = queries;
        this.storageRetry = storageRetry;
        this.reviews = reviews;
    }

    @GetMapping("/dashboard")
    public ApiSuccessResponse<Dashboard> dashboard(Authentication authentication) {
        return ApiSuccessResponse.of(queries.dashboard(authentication));
    }

    @GetMapping("/reviews")
    public ApiSuccessResponse<PageResponse<AdminReviewItem>> reviews(
            @RequestParam(name = "restaurantId", required = false) Long restaurantId,
            @RequestParam(name = "memberId", required = false) Long memberId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(queries.reviews(authentication, restaurantId, memberId, page, size));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiSuccessResponse<ReviewDeleteResult> deleteReview(@PathVariable("reviewId") long reviewId,
                                                               Authentication authentication) {
        return ApiSuccessResponse.of(reviews.deleteAsAdmin(reviewId, authentication).result());
    }

    @GetMapping("/members")
    public ApiSuccessResponse<PageResponse<AdminMemberItem>> members(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(queries.members(authentication, keyword, page, size));
    }

    @GetMapping("/members/{memberId}")
    public ApiSuccessResponse<AdminMemberItem> member(@PathVariable("memberId") long memberId,
                                                      Authentication authentication) {
        return ApiSuccessResponse.of(queries.member(authentication, memberId));
    }

    @GetMapping("/storage-deletions")
    public ApiSuccessResponse<PageResponse<StorageDeletionItem>> storageDeletions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(queries.storageDeletions(authentication, page, size));
    }

    @PostMapping("/storage-deletions/retry")
    public ApiSuccessResponse<RetryResult> retryStorageDeletions(Authentication authentication) {
        return ApiSuccessResponse.of(storageRetry.retry(authentication));
    }
}
