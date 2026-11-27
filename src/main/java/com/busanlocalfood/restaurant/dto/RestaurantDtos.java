package com.busanlocalfood.restaurant.dto;

import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 맛집 API 요청과 응답 형식 (API_SPEC 3.1, 4.1~4.5). 화면 폼과 API 본문이 같은 입력 형식을 쓴다.
 *
 * 이미지는 F1-03에서 연결했다. 찜(F1-06), 리뷰(F1-04), AI 요약(F1-05)은 아직 없다. 그래서 상세 응답의 favoriteCount는 0,
 * isFavorited는 false로 준다. 이 값들은 실제 데이터가 아니라 기능이 없다는 뜻의 기본값이다.
 */
public final class RestaurantDtos {

    private RestaurantDtos() {
    }

    /** 맛집 등록·수정 입력. 값의 검증은 RestaurantInputRules가 한다. */
    public record RestaurantRequest(String name, Long categoryId, String address, String phone, String description,
                                    BigDecimal latitude, BigDecimal longitude) {
    }

    public record CategoryResponse(Long id, String name, int sortOrder) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
        }
    }

    public record CategoryRef(Long id, String name) {
        static CategoryRef from(Category category) {
            return new CategoryRef(category.getId(), category.getName());
        }
    }

    public record OwnerRef(long memberId, String nickname) {
    }

    /**
     * 목록 항목 (API_SPEC 4.1). thumbnailUrl은 대표 이미지(sort_order가 가장 작은 것)의 URL이다.
     * 이미지가 없거나 S3 설정이 없으면 null이다.
     */
    public record RestaurantSummary(Long id, String name, CategoryRef category, String address,
                                    BigDecimal averageRating, int reviewCount, String thumbnailUrl,
                                    LocalDateTime createdAt) {
        public static RestaurantSummary from(Restaurant restaurant, String thumbnailUrl) {
            return new RestaurantSummary(restaurant.getId(), restaurant.getName(),
                    CategoryRef.from(restaurant.getCategory()), restaurant.getAddress(),
                    restaurant.getAverageRating(), restaurant.getReviewCount(), thumbnailUrl,
                    restaurant.getCreatedAt());
        }
    }

    /** 이미지 항목 (API_SPEC 5.1). url은 S3 설정이 없으면 null이다. */
    public record ImageSummary(Long id, String url, int sortOrder) {
    }

    /** 이미지 삭제 응답 (API_SPEC 5.2). storageDeleted가 false면 DB 삭제는 끝났고 S3 삭제는 재시도 대기다. */
    public record ImageDeleteResult(Long imageId, boolean storageDeleted) {
    }

    /** 상세 (API_SPEC 4.2). isOwner는 인증된 회원 기준으로 계산한다. 비회원은 false다. */
    public record RestaurantDetail(Long id, String name, CategoryRef category, String address, String phone,
                                   String description, BigDecimal latitude, BigDecimal longitude,
                                   BigDecimal averageRating, int reviewCount, OwnerRef owner,
                                   List<ImageSummary> images, int favoriteCount, boolean isFavorited,
                                   boolean isOwner, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static RestaurantDetail of(Restaurant restaurant, boolean isOwner, List<ImageSummary> images) {
            return new RestaurantDetail(restaurant.getId(), restaurant.getName(),
                    CategoryRef.from(restaurant.getCategory()), restaurant.getAddress(), restaurant.getPhone(),
                    restaurant.getDescription(), restaurant.getLatitude(), restaurant.getLongitude(),
                    restaurant.getAverageRating(), restaurant.getReviewCount(),
                    new OwnerRef(restaurant.getMember().getId(), restaurant.getMember().getNickname()),
                    images, 0, false, isOwner, restaurant.getCreatedAt(), restaurant.getUpdatedAt());
        }
    }

    /**
     * 삭제 응답 (API_SPEC 4.5). deletedImageCount는 함께 지운 이미지 행 수다.
     * storageDeleteFailedCount는 S3 삭제가 실패해 storage_delete_task에 남은 객체 수다. DB 삭제는 이미 끝났다.
     */
    public record DeleteResult(Long restaurantId, int deletedImageCount, int storageDeleteFailedCount) {
    }

    /** 목록 응답의 data 구조 (API_SPEC 1.2). */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
