package com.busanlocalfood.review.web;

import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse;
import com.busanlocalfood.review.service.ReviewSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 리뷰 요약 API (API_SPEC 7.1). 공개 조회다. 요약 실패는 오류가 아니라 status: UNAVAILABLE로 200 응답한다.
 */
@RestController
public class ReviewSummaryApiController {

    private final ReviewSummaryService summaryService;

    public ReviewSummaryApiController(ReviewSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/api/restaurants/{id}/summary")
    public ApiSuccessResponse<SummaryResponse> summary(@PathVariable("id") long id) {
        return ApiSuccessResponse.of(summaryService.summaryFor(id));
    }
}
