package com.busanlocalfood.admin.service;

import com.busanlocalfood.admin.dto.AdminDtos.AdminMemberItem;
import com.busanlocalfood.admin.dto.AdminDtos.AdminRestaurantItem;
import com.busanlocalfood.admin.dto.AdminDtos.AdminReviewItem;
import com.busanlocalfood.admin.dto.AdminDtos.Dashboard;
import com.busanlocalfood.admin.dto.AdminDtos.OwnerContact;
import com.busanlocalfood.admin.dto.AdminDtos.RestaurantRef;
import com.busanlocalfood.admin.dto.AdminDtos.StorageDeletionItem;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.security.AdminAccess;
import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.repository.RestaurantImageRepository;
import com.busanlocalfood.image.repository.StorageDeleteTaskRepository;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryRef;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.repository.RestaurantSearch;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.domain.Review;
import com.busanlocalfood.review.repository.ReviewRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 조회 서비스 (FUNCTION_SPEC F1-09). 대시보드, 맛집·리뷰·회원 목록과 상세, S3 삭제 대기 목록.
 * 모든 진입점에서 ADMIN 권한을 먼저 확인한다. 목록은 페이지 단위로만 읽고, 항목마다 추가 조회를 하지 않는다.
 */
@Service
public class AdminQueryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int KEYWORD_MAX = 50;
    static final int RECENT_DAYS = 7;

    private final MemberRepository members;
    private final RestaurantRepository restaurants;
    private final ReviewRepository reviews;
    private final RestaurantImageRepository images;
    private final StorageDeleteTaskRepository tasks;
    private final Clock clock;

    public AdminQueryService(MemberRepository members, RestaurantRepository restaurants, ReviewRepository reviews,
                             RestaurantImageRepository images, StorageDeleteTaskRepository tasks, Clock clock) {
        this.members = members;
        this.restaurants = restaurants;
        this.reviews = reviews;
        this.images = images;
        this.tasks = tasks;
        this.clock = clock;
    }

    /** 대시보드 집계 (5-1). 최근 7일은 서비스 시간대(Asia/Seoul) 기준 오늘을 포함한 7일이다. */
    @Transactional(readOnly = true)
    public Dashboard dashboard(Authentication actor) {
        AdminAccess.requireAdmin(actor);
        LocalDateTime since = LocalDate.now(clock).minusDays(RECENT_DAYS - 1).atStartOfDay();
        return new Dashboard(
                members.count(),
                restaurants.count(),
                reviews.count(),
                restaurants.countByCreatedAtGreaterThanEqual(since),
                reviews.countByCreatedAtGreaterThanEqual(since),
                tasks.count());
    }

    /** 맛집 목록 (5-2). 검색어, 카테고리, 정렬, 페이지를 받는다. 작성자와 카테고리는 같은 쿼리에서 읽는다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantItem> restaurants(Authentication actor, String keyword, Long categoryId,
                                                         String sort, int page, int size) {
        AdminAccess.requireAdmin(actor);
        String kw = normalizedKeyword(keyword);
        int safePage = requirePage(page);
        int safeSize = requireSize(size);
        Page<Restaurant> result = restaurants.findAll(RestaurantSearch.of(categoryId, kw),
                PageRequest.of(safePage, safeSize, RestaurantService.parseSort(sort)));

        List<Long> ids = result.getContent().stream().map(Restaurant::getId).toList();
        Map<Long, Integer> imageCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : images.countByRestaurantIds(ids)) {
                imageCounts.put((Long) row[0], ((Long) row[1]).intValue());
            }
        }
        List<AdminRestaurantItem> items = result.getContent().stream().map(r -> new AdminRestaurantItem(
                r.getId(), r.getName(),
                new CategoryRef(r.getCategory().getId(), r.getCategory().getName()),
                r.getAddress(), r.getAverageRating(), r.getReviewCount(),
                imageCounts.getOrDefault(r.getId(), 0),
                owner(r.getMember()), r.getCreatedAt())).toList();
        return pageOf(items, result);
    }

    /** 리뷰 목록 (5-7). 맛집 ID와 회원 ID는 선택 필터다. 최신순이다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewItem> reviews(Authentication actor, Long restaurantId, Long memberId,
                                                 int page, int size) {
        AdminAccess.requireAdmin(actor);
        Page<Review> result = reviews.findAdminPage(restaurantId, memberId,
                PageRequest.of(requirePage(page), requireSize(size)));
        List<AdminReviewItem> items = result.getContent().stream().map(r -> new AdminReviewItem(
                r.getId(),
                new RestaurantRef(r.getRestaurant().getId(), r.getRestaurant().getName()),
                owner(r.getMember()),
                r.getRating(), r.getContent(), r.getCreatedAt())).toList();
        return pageOf(items, result);
    }

    /** 회원 목록 (5-9). 이메일 또는 닉네임 검색. 맛집 수와 리뷰 수는 회원 ID 목록에 대한 집계 두 번으로 읽는다. */
    @Transactional(readOnly = true)
    public PageResponse<AdminMemberItem> members(Authentication actor, String keyword, int page, int size) {
        AdminAccess.requireAdmin(actor);
        String kw = normalizedKeyword(keyword);
        String pattern = kw == null ? null
                : "%" + RestaurantSearch.escapeLike(kw.toLowerCase(Locale.ROOT)) + "%";
        Page<Member> result = members.searchForAdmin(pattern, PageRequest.of(requirePage(page), requireSize(size)));

        List<Long> ids = result.getContent().stream().map(Member::getId).toList();
        Map<Long, Long> restaurantCounts = new HashMap<>();
        Map<Long, Long> reviewCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : restaurants.countByMemberIds(ids)) {
                restaurantCounts.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : reviews.countByMemberIds(ids)) {
                reviewCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        List<AdminMemberItem> items = result.getContent().stream().map(m -> new AdminMemberItem(
                m.getId(), m.getEmail(), m.getNickname(), m.getRole().name(), m.isGoogleLinked(),
                restaurantCounts.getOrDefault(m.getId(), 0L), reviewCounts.getOrDefault(m.getId(), 0L),
                m.getCreatedAt())).toList();
        return pageOf(items, result);
    }

    /** 회원 상세 (5-10). 읽기 전용이다. 없는 회원은 MEMBER_NOT_FOUND다. */
    @Transactional(readOnly = true)
    public AdminMemberItem member(Authentication actor, long memberId) {
        AdminAccess.requireAdmin(actor);
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return new AdminMemberItem(member.getId(), member.getEmail(), member.getNickname(),
                member.getRole().name(), member.isGoogleLinked(),
                restaurants.countByMemberId(memberId), reviews.countByMemberId(memberId), member.getCreatedAt());
    }

    /** S3 삭제 재시도 대기 목록 (5-11). 오래된 순이다. */
    @Transactional(readOnly = true)
    public PageResponse<StorageDeletionItem> storageDeletions(Authentication actor, int page, int size) {
        AdminAccess.requireAdmin(actor);
        Page<StorageDeleteTask> result = tasks.findAllByOrderByCreatedAtAscIdAsc(
                PageRequest.of(requirePage(page), requireSize(size)));
        List<StorageDeletionItem> items = result.getContent().stream().map(t -> new StorageDeletionItem(
                t.getId(), t.getS3Key(), t.getRetryCount(), t.getLastError(),
                t.getCreatedAt(), t.getUpdatedAt())).toList();
        return pageOf(items, result);
    }

    private static OwnerContact owner(Member member) {
        return new OwnerContact(member.getId(), member.getNickname(), member.getEmail());
    }

    /** 검색어. 공백만 있으면 없는 것으로 본다. 길이 제한을 넘으면 VALIDATION_FAILED다. */
    static String normalizedKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return trimmed;
    }

    static int requirePage(int page) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return page;
    }

    static int requireSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return size;
    }

    private static <T, E> PageResponse<T> pageOf(List<T> items, Page<E> result) {
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
