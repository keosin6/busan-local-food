package com.busanlocalfood.restaurant.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.domain.RestaurantImage;
import com.busanlocalfood.image.repository.RestaurantImageRepository;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.repository.RestaurantSearch;
import com.busanlocalfood.review.repository.ReviewRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 맛집 관리 서비스 (FUNCTION_SPEC F1-02, F1-03). 작성자 확인은 여기서 한다. 화면과 API 컨트롤러는 이 결과만 쓴다.
 *
 * 수정과 삭제는 작성자 본인만 허용한다. 관리자는 이 일반 회원 흐름에서 작성자 검사를 우회하지 않는다 (F1-09의 관리자 API를 쓴다).
 * 삭제는 DB 행을 완전히 지운다. 소프트 삭제는 쓰지 않는다. 이미지 객체는 DB 커밋 뒤에 S3에서 지운다.
 */
@Service
public class RestaurantService {

    private static final Sort LATEST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    private static final Sort BY_RATING = Sort.by(Sort.Direction.DESC, "averageRating")
            .and(Sort.by(Sort.Direction.DESC, "createdAt"))
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final RestaurantRepository restaurants;
    private final CategoryRepository categories;
    private final MemberRepository members;
    private final RestaurantImageRepository images;
    private final ReviewRepository reviews;
    private final StorageCleanup storageCleanup;
    private final ImageUrls imageUrls;
    private final TransactionTemplate tx;

    public RestaurantService(RestaurantRepository restaurants, CategoryRepository categories, MemberRepository members,
                             RestaurantImageRepository images, ReviewRepository reviews, StorageCleanup storageCleanup,
                             ImageUrls imageUrls, PlatformTransactionManager transactionManager) {
        this.restaurants = restaurants;
        this.categories = categories;
        this.members = members;
        this.images = images;
        this.reviews = reviews;
        this.storageCleanup = storageCleanup;
        this.imageUrls = imageUrls;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 카테고리 목록 (API_SPEC 3.1). 누구나 조회한다. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::from).toList();
    }

    /** 맛집 목록과 검색 (API_SPEC 4.1). 비회원도 조회한다. 항목마다 대표 이미지 URL을 붙인다. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummary> list(Long categoryId, String rawKeyword, String sort, int page, int size) {
        String keyword = RestaurantInputRules.keyword(rawKeyword);
        int safePage = RestaurantInputRules.page(page);
        int safeSize = RestaurantInputRules.size(size);
        Sort order = parseSort(sort);
        Page<Restaurant> result = restaurants.findAll(RestaurantSearch.of(categoryId, keyword),
                PageRequest.of(safePage, safeSize, order));

        List<Long> ids = result.getContent().stream().map(Restaurant::getId).toList();
        Map<Long, String> thumbnails = thumbnailsOf(ids);
        List<RestaurantSummary> content = result.getContent().stream()
                .map(r -> RestaurantSummary.from(r, thumbnails.get(r.getId())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** 맛집 상세 (API_SPEC 4.2). viewerId가 null이면 비회원이다. */
    @Transactional(readOnly = true)
    public RestaurantDetail detail(long restaurantId, Long viewerId) {
        Restaurant restaurant = find(restaurantId);
        return RestaurantDetail.of(restaurant, viewerId != null && restaurant.isOwnedBy(viewerId),
                imagesOf(restaurantId));
    }

    /** 등록 (API_SPEC 4.3). 작성자는 현재 인증된 회원이다. 새 맛집에는 이미지가 없다. */
    @Transactional
    public RestaurantDetail create(long memberId, RestaurantRequest request) {
        Member member = members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        RestaurantInputRules.Validated input = RestaurantInputRules.validate(request);
        Category category = findCategory(input.categoryId());
        Restaurant saved = restaurants.save(Restaurant.create(member, category, input.fields()));
        return RestaurantDetail.of(saved, true, List.of());
    }

    /** 수정 (API_SPEC 4.4). 작성자 본인만 할 수 있다. 이미지는 이 요청으로 바뀌지 않는다. */
    @Transactional
    public RestaurantDetail update(long restaurantId, long memberId, RestaurantRequest request) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        RestaurantInputRules.Validated input = RestaurantInputRules.validate(request);
        Category category = findCategory(input.categoryId());
        restaurant.update(category, input.fields());
        return RestaurantDetail.of(restaurant, true, imagesOf(restaurantId));
    }

    /**
     * 삭제 (API_SPEC 4.5). 작성자 본인만 할 수 있다.
     *
     * 1) DB 트랜잭션: 맛집 이미지마다 storage_delete_task에 키를 기록하고, 이미지 행과 맛집 행을 지운다. 커밋한다.
     *    이 단계가 실패하면 S3에는 아무 일도 하지 않는다.
     * 2) 커밋 뒤: 각 키의 S3 객체를 지운다. 성공하면 추적 행을 지우고, 실패하면 추적 행을 남긴다.
     *    S3 실패는 맛집 삭제를 되돌리지 않는다. 응답의 storageDeleteFailedCount로 알린다.
     *
     * 리뷰, 찜, AI 요약 행의 삭제는 해당 기능(F1-04~F1-06)이 들어올 때 같은 트랜잭션에 추가한다.
     */
    public DeleteResult delete(long restaurantId, long memberId) {
        List<String> s3Keys = tx.execute(status -> {
            // 맛집 행을 먼저 잠근다. 같은 맛집에 대한 이미지 업로드·개별 삭제는 이 잠금이 풀릴 때까지 기다린다.
            // 잠금을 잡은 뒤에 이미지 목록을 읽어야, 커밋되지 않은 업로드가 목록에서 빠지지 않는다.
            Restaurant restaurant = findForUpdate(restaurantId);
            requireOwner(restaurant, memberId);
            List<RestaurantImage> owned = images.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId);
            List<String> keys = new ArrayList<>();
            for (RestaurantImage image : owned) {
                storageCleanup.recordPending(image.getS3Key());
                keys.add(image.getS3Key());
            }
            images.deleteAll(owned);
            // 리뷰는 같은 트랜잭션에서 지운다. 맛집 행이 사라지면 ON DELETE CASCADE가 남은 리뷰도 지우지만, 순서를 명시한다.
            reviews.deleteAll(reviews.findByRestaurantId(restaurantId));
            restaurants.delete(restaurant);
            return keys;
        });

        int storageFailed = 0;
        for (String s3Key : s3Keys) {
            if (!storageCleanup.deleteAfterCommit(s3Key)) {
                storageFailed++;
            }
        }
        return new DeleteResult(restaurantId, s3Keys.size(), storageFailed);
    }

    /** 수정 화면에 채울 값. 작성자가 아니면 FORBIDDEN이다. */
    @Transactional(readOnly = true)
    public RestaurantDetail loadForEdit(long restaurantId, long memberId) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        return RestaurantDetail.of(restaurant, true, imagesOf(restaurantId));
    }

    private Restaurant find(long restaurantId) {
        return restaurants.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    /** 맛집 행을 잠그고 읽는다. 쓰기 트랜잭션(삭제)에서만 쓴다. */
    private Restaurant findForUpdate(long restaurantId) {
        return restaurants.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private Category findCategory(long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private List<ImageSummary> imagesOf(long restaurantId) {
        return images.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId).stream()
                .map(image -> new ImageSummary(image.getId(), imageUrls.urlOf(image.getS3Key()), image.getSortOrder()))
                .toList();
    }

    /** 목록의 대표 이미지 URL. 맛집 ID마다 sort_order가 가장 작은 이미지 하나를 고른다. 이미지가 없으면 항목이 없다. */
    private Map<Long, String> thumbnailsOf(List<Long> restaurantIds) {
        Map<Long, String> thumbnails = new HashMap<>();
        if (restaurantIds.isEmpty()) {
            return thumbnails;
        }
        for (RestaurantImage image : images.findByRestaurantIdInOrderBySortOrderAscIdAsc(restaurantIds)) {
            thumbnails.putIfAbsent(image.getRestaurant().getId(), imageUrls.urlOf(image.getS3Key()));
        }
        return thumbnails;
    }

    private static void requireOwner(Restaurant restaurant, long memberId) {
        if (!restaurant.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank() || "latest".equals(sort)) {
            return LATEST;
        }
        if ("rating".equals(sort)) {
            return BY_RATING;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
}
