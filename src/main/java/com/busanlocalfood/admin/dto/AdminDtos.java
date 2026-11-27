package com.busanlocalfood.admin.dto;

import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 API 응답 형식 (FUNCTION_SPEC F1-09, API_SPEC 11). 비밀번호 해시, Google sub, Refresh Token 같은 내부 값은 넣지 않는다.
 * 회원 정보는 관리자 화면이 필요한 이메일과 닉네임, 역할, Google 연동 여부만 담는다.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 대시보드 집계 (5-1). 최근 7일은 Asia/Seoul 기준 오늘을 포함한 7일이다. */
    public record Dashboard(long memberCount, long restaurantCount, long reviewCount,
                            long newRestaurantsLast7Days, long newReviewsLast7Days,
                            long pendingStorageDeletionCount) {
    }

    /** 작성자 연락 정보. 관리자 화면에서만 쓴다. */
    public record OwnerContact(long memberId, String nickname, String email) {
    }

    /** 관리자 맛집 목록 항목 (5-2). */
    public record AdminRestaurantItem(Long id, String name, CategoryRef category, String address,
                                      BigDecimal averageRating, int reviewCount, int imageCount,
                                      OwnerContact owner, LocalDateTime createdAt) {
    }

    public record RestaurantRef(Long id, String name) {
    }

    /** 관리자 리뷰 목록 항목 (5-7). */
    public record AdminReviewItem(Long id, RestaurantRef restaurant, OwnerContact author, int rating, String content,
                                  LocalDateTime createdAt) {
    }

    /** 관리자 회원 목록·상세 항목 (5-9, 5-10). 읽기 전용이다. */
    public record AdminMemberItem(long memberId, String email, String nickname, String role, boolean googleLinked,
                                  long restaurantCount, long reviewCount, LocalDateTime createdAt) {
    }

    /** S3 삭제 재시도 대기 항목 (5-11). lastError는 안전 메시지만 담는다. */
    public record StorageDeletionItem(Long id, String s3Key, int retryCount, String lastError,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 수동 재시도 결과 (5-12). 실패 한 건은 전체 오류가 아니다. */
    public record RetryResult(int processed, int succeeded, int failed) {
    }
}
