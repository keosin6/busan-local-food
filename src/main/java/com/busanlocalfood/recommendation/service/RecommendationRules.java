package com.busanlocalfood.recommendation.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;

/** 취향 입력 검증. 누락이거나 1~200자 범위를 벗어나면 VALIDATION_FAILED다 (API_SPEC 9.1). */
public final class RecommendationRules {

    static final int PREFERENCE_MAX_CHARS = 200;

    private RecommendationRules() {
    }

    /** 앞뒤 공백을 지운 취향 문장을 돌려준다. */
    public static String preference(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String trimmed = raw.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < 1 || length > PREFERENCE_MAX_CHARS) {
            throw invalid();
        }
        return trimmed;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
}
