package com.busanlocalfood.recommendation.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationRequest;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RestaurantRecommendationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 추천 API (API_SPEC 9.1). JWT 인증이 필요하다 (SecurityConfig의 /api/** 규칙). 역할은 USER와 ADMIN 모두 허용한다.
 * 취향 검증과 추천 처리는 서비스가 한다. 응답은 기존 API와 같은 ApiSuccessResponse를 쓴다.
 */
@RestController
public class RecommendationApiController {

    private final RestaurantRecommendationService recommendations;

    public RecommendationApiController(RestaurantRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/api/recommendations")
    public ApiSuccessResponse<RecommendationResponse> recommend(
            @RequestBody(required = false) RecommendationRequest request,
            Authentication authentication) {
        requireMemberId(authentication);
        String preference = request == null ? null : request.preference();
        return ApiSuccessResponse.of(recommendations.recommend(preference));
    }

    /** 인증 주체가 회원이어야 한다. 없으면 TOKEN_INVALID(401)다. */
    private static void requireMemberId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
