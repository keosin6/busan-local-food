package com.busanlocalfood.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 리뷰 API 요청과 응답 형식 (API_SPEC 6.1~6.3). */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * 리뷰 작성 입력. rating은 BigDecimal로 받아 정수 여부를 직접 확인한다.
     * Integer로 받으면 Jackson이 5.5를 5로 조용히 바꾸므로 쓰지 않는다. 값의 검증은 ReviewInputRules가 한다.
     */
    public record ReviewRequest(BigDecimal rating, String content) {
    }

    public record AuthorRef(long memberId, String nickname) {
    }

    /** 목록 항목 (API_SPEC 6.1). isMine은 조회한 회원이 작성자일 때만 true다. */
    public record ReviewItem(Long id, int rating, String content, AuthorRef author, boolean isMine,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 작성 응답 (API_SPEC 6.2). 맛집 평균과 리뷰 수는 DB에서 다시 계산한 값이다. */
    public record ReviewCreated(Long id, int rating, String content, BigDecimal restaurantAverageRating,
                                int restaurantReviewCount) {
    }

    /**
     * AI 리뷰 요약 응답 (API_SPEC 7.1). status는 READY, NOT_ENOUGH_REVIEWS, UNAVAILABLE 중 하나다.
     * cached와 createdAt은 READY일 때만, message는 NOT_ENOUGH_REVIEWS와 UNAVAILABLE일 때만 값을 가진다.
     */
    public record SummaryResponse(String status,
                                  String summary,
                                  int reviewCount,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Boolean cached,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime createdAt,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) String message) {
    }

    /** 삭제 응답 (API_SPEC 6.3). */
    public record ReviewDeleteResult(BigDecimal restaurantAverageRating, int restaurantReviewCount) {
    }
}
