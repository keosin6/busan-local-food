package com.busanlocalfood.review.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import java.math.BigDecimal;

/** 리뷰 입력 검증 (FUNCTION_SPEC F1-04 작성 흐름 1번). 평점은 1~5 정수, 내용은 공백을 뺀 뒤 1~500자다. */
final class ReviewInputRules {

    static final int CONTENT_MAX = 500;

    private ReviewInputRules() {
    }

    record Validated(int rating, String content) {
    }

    static Validated validate(ReviewRequest request) {
        if (request == null || request.rating() == null) {
            throw invalid();
        }
        BigDecimal given = request.rating();
        // 1~5 범위 안이고 소수 부분이 없어야 한다. 5.0은 정수로 보고, 5.5는 거부한다.
        if (given.compareTo(BigDecimal.ONE) < 0 || given.compareTo(BigDecimal.valueOf(5)) > 0
                || given.stripTrailingZeros().scale() > 0) {
            throw invalid();
        }
        int rating = given.intValueExact();
        if (request.content() == null) {
            throw invalid();
        }
        String content = request.content().trim();
        if (content.isEmpty() || content.length() > CONTENT_MAX) {
            throw invalid();
        }
        return new Validated(rating, content);
    }

    static int page(int page) {
        if (page < 0) {
            throw invalid();
        }
        return page;
    }

    /** 목록 크기. 기본 10, 최대 50 (API_SPEC 6.1). */
    static int size(int size) {
        if (size < 1 || size > 50) {
            throw invalid();
        }
        return size;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
}
