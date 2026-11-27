package com.busanlocalfood.recommendation.service;

import com.busanlocalfood.ai.service.ChatCompletionClient.AiResponseInvalidException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 추천 응답 파서 (FUNCTION_SPEC F1-07). 응답은 JSON 배열 하나여야 한다.
 * 각 항목은 정수 restaurantId와 비어 있지 않은 reason을 가진다. 어긋나면 AiResponseInvalidException이다.
 * 파싱 오류 메시지에는 AI 응답 원문을 넣지 않는다.
 */
public final class RecommendationParser {

    static final int REASON_MAX_CHARS = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 검증을 통과한 항목. restaurantId가 후보에 있는지는 서비스가 확인한다. */
    public record AiPick(long restaurantId, String reason) {
    }

    private RecommendationParser() {
    }

    public static List<AiPick> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("AI 응답이 비어 있습니다.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw invalid("AI 응답이 JSON이 아닙니다.");
        }
        if (root == null || !root.isArray()) {
            throw invalid("AI 응답이 JSON 배열이 아닙니다.");
        }
        List<AiPick> picks = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isObject()) {
                throw invalid("AI 응답 항목이 객체가 아닙니다.");
            }
            JsonNode id = item.get("restaurantId");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
                throw invalid("AI 응답의 restaurantId가 올바르지 않습니다.");
            }
            JsonNode reason = item.get("reason");
            if (reason == null || !reason.isTextual() || reason.asText().isBlank()) {
                throw invalid("AI 응답의 reason이 없습니다.");
            }
            picks.add(new AiPick(id.asLong(), truncate(reason.asText().trim(), REASON_MAX_CHARS)));
        }
        return picks;
    }

    static String truncate(String value, int maxChars) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxChars) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxChars));
    }

    private static AiResponseInvalidException invalid(String message) {
        return new AiResponseInvalidException(message);
    }
}
