package com.busanlocalfood.recommendation.service;

import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiResponseInvalidException;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiUnavailableException;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.Recommendation;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RecommendationParser.AiPick;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * AI 맛집 추천 (FUNCTION_SPEC F1-07, API_SPEC 9.1).
 *
 * 흐름: 취향 검증 → 최근 등록 맛집 최대 100개를 후보로 조회 → 후보가 없으면 AI를 부르지 않는다 →
 * OpenRouter 호출(F1-05와 같은 ChatCompletionClient, 같은 OPENROUTER_MODEL) → JSON 배열 파싱 →
 * 후보에 있는 restaurantId만 남기고 최대 5개 → 카드 정보는 DB 후보 데이터로 채운다.
 *
 * 추천 결과는 저장하지 않는다. 트랜잭션을 열지 않으므로 AI 호출 동안 DB 연결을 잡지 않는다.
 */
@Service
public class RestaurantRecommendationService {

    static final int MAX_CANDIDATES = 100;
    static final int MAX_RESULTS = 5;
    static final String MESSAGE_NO_CANDIDATES = "등록된 맛집이 없어 추천할 수 없습니다.";
    static final String MESSAGE_NO_MATCH = "이 취향에 맞는 추천 결과가 없습니다. 취향을 바꿔 다시 시도해 보세요.";

    private final RestaurantRepository restaurants;
    private final ChatCompletionClient client;

    public RestaurantRecommendationService(RestaurantRepository restaurants, ChatCompletionClient client) {
        this.restaurants = restaurants;
        this.client = client;
    }

    /** 취향에 맞는 맛집을 추천한다. 취향이 잘못되면 VALIDATION_FAILED, AI 오류는 AI_UNAVAILABLE 또는 AI_RESPONSE_INVALID다. */
    public RecommendationResponse recommend(String rawPreference) {
        String preference = RecommendationRules.preference(rawPreference);
        List<Restaurant> candidates = restaurants.findTop100ByOrderByCreatedAtDescIdDesc().stream()
                .limit(MAX_CANDIDATES)
                .toList();
        if (candidates.isEmpty()) {
            return new RecommendationResponse(List.of(), 0, MESSAGE_NO_CANDIDATES);
        }

        String answer = ask(preference, candidates);
        List<Recommendation> recommendations = toRecommendations(parse(answer), candidates);
        String message = recommendations.isEmpty() ? MESSAGE_NO_MATCH : null;
        return new RecommendationResponse(recommendations, candidates.size(), message);
    }

    private String ask(String preference, List<Restaurant> candidates) {
        try {
            return client.complete(RecommendationPrompt.build(preference, candidates));
        } catch (AiUnavailableException e) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        } catch (AiResponseInvalidException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private static List<AiPick> parse(String answer) {
        try {
            return RecommendationParser.parse(answer);
        } catch (AiResponseInvalidException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    /**
     * AI가 고른 ID 중 후보에 있는 것만 남긴다. 같은 ID는 처음 것만 쓴다. 최대 5개다.
     * 카드의 이름, 카테고리, 평점은 후보 DB 데이터에서 가져온다. 결과가 없으면 안내 문구를 붙인다.
     */
    private static List<Recommendation> toRecommendations(List<AiPick> picks, List<Restaurant> candidates) {
        Map<Long, Restaurant> byId = new HashMap<>();
        for (Restaurant r : candidates) {
            byId.put(r.getId(), r);
        }
        List<Recommendation> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AiPick pick : picks) {
            if (result.size() >= MAX_RESULTS) {
                break;
            }
            Restaurant restaurant = byId.get(pick.restaurantId());
            if (restaurant == null || !seen.add(pick.restaurantId())) {
                continue;
            }
            result.add(new Recommendation(restaurant.getId(), restaurant.getName(),
                    restaurant.getCategory().getName(), restaurant.getAverageRating(), pick.reason()));
        }
        return result;
    }
}
