package com.busanlocalfood.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

/** AI 추천 API 요청과 응답 (API_SPEC 9.1, FUNCTION_SPEC F1-07). */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** 요청 본문. preference는 1~200자다. 검증은 RecommendationRules가 한다. */
    public record RecommendationRequest(String preference) {
    }

    /**
     * 추천 맛집 한 건. 이름, 카테고리, 평점은 DB 후보 데이터에서 가져온다. AI 응답의 값은 쓰지 않는다.
     * reason만 AI가 쓴다.
     */
    public record Recommendation(long restaurantId, String name, String category, BigDecimal averageRating,
                                 String reason) {
    }

    /** 추천 응답. 후보가 없거나 결과가 없으면 recommendations는 비어 있고 message에 안내가 있다. */
    public record RecommendationResponse(List<Recommendation> recommendations, int candidateCount, String message) {
    }
}
