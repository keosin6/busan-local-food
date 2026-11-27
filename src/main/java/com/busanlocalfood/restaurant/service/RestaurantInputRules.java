package com.busanlocalfood.restaurant.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.restaurant.domain.Restaurant.RestaurantFields;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 맛집 입력 검증 규칙 (FUNCTION_SPEC F1-02 등록 흐름, DATABASE_ERD 2.3).
 * 실패하면 VALIDATION_FAILED를 던진다. 입력값 원문은 메시지에 넣지 않는다.
 * 카테고리 존재 여부는 서비스가 DB로 확인한다 (CATEGORY_NOT_FOUND).
 */
final class RestaurantInputRules {

    static final int NAME_MAX = 50;
    static final int ADDRESS_MAX = 200;
    static final int DESCRIPTION_MAX = 2000;
    static final int PHONE_MAX = 20;
    static final int KEYWORD_MAX = 50;
    static final int SIZE_MAX = 50;
    static final int DEFAULT_SIZE = 12;
    /** 위도·경도는 DECIMAL(10,7)이므로 소수 일곱 자리까지 받는다. */
    static final int COORDINATE_SCALE = 7;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9-]+$");

    private RestaurantInputRules() {
    }

    /** 검증이 끝난 등록·수정 입력. */
    record Validated(long categoryId, RestaurantFields fields) {
    }

    static Validated validate(RestaurantRequest request) {
        if (request == null) {
            throw invalid();
        }
        String name = requireText(request.name(), NAME_MAX);
        if (request.categoryId() == null) {
            throw invalid();
        }
        String address = requireText(request.address(), ADDRESS_MAX);
        String description = requireText(request.description(), DESCRIPTION_MAX);
        String phone = optionalPhone(request.phone());
        // 위도와 경도는 둘 다 입력하거나 둘 다 비워야 한다. 한쪽만 있으면 좌표를 알 수 없다.
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw invalid();
        }
        BigDecimal latitude = optionalCoordinate(request.latitude(), 90);
        BigDecimal longitude = optionalCoordinate(request.longitude(), 180);
        return new Validated(request.categoryId(),
                new RestaurantFields(name, address, phone, description, latitude, longitude));
    }

    /** 목록 검색어. 공백을 지운 뒤 비어 있으면 null, 최대 50자를 넘으면 거부한다. */
    static String keyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > KEYWORD_MAX) {
            throw invalid();
        }
        return trimmed;
    }

    static int page(int page) {
        if (page < 0) {
            throw invalid();
        }
        return page;
    }

    static int size(int size) {
        if (size < 1 || size > SIZE_MAX) {
            throw invalid();
        }
        return size;
    }

    private static String requireText(String raw, int max) {
        if (raw == null) {
            throw invalid();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw invalid();
        }
        return trimmed;
    }

    private static String optionalPhone(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > PHONE_MAX || !PHONE_PATTERN.matcher(trimmed).matches()) {
            throw invalid();
        }
        return trimmed;
    }

    private static BigDecimal optionalCoordinate(BigDecimal value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw invalid();
        }
        if (value.stripTrailingZeros().scale() > COORDINATE_SCALE) {
            throw invalid();
        }
        return value.setScale(COORDINATE_SCALE);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
}
