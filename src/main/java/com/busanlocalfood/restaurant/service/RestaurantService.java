package com.busanlocalfood.restaurant.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.security.AdminAccess;
import org.springframework.security.core.Authentication;
import com.busanlocalfood.favorite.repository.FavoriteRepository;
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
import com.busanlocalfood.review.repository.ReviewSummaryRepository;
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

    private static final int SIZE_MAX = 50;

    private final RestaurantRepository restaurants;
    private final CategoryRepository categories;
    private final MemberRepository members;
    private final RestaurantImageRepository images;
    private final ReviewRepository reviews;
    private final ReviewSummaryRepository summaries;
    private final FavoriteRepository favorites;
    private final StorageCleanup storageCleanup;
    private final ImageUrls imageUrls;
    private final TransactionTemplate tx;

    public RestaurantService(RestaurantRepository restaurants, CategoryRepository categories, MemberRepository members,
                             RestaurantImageRepository images, ReviewRepository reviews,
                             ReviewSummaryRepository summaries, FavoriteRepository favorites,
                             StorageCleanup storageCleanup, ImageUrls imageUrls,
                             PlatformTransactionManager transactionManager) {
        this.restaurants = restaurants;
        this.categories = categories;
        this.members = members;
        this.images = images;
        this.reviews = reviews;
        this.summaries = summaries;
        this.favorites = favorites;
        this.storageCleanup = storageCleanup;
        this.imageUrls = imageUrls;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 카테고리 목록 (API_SPEC 3.1). 누구나 조회한다. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::from).toList();
    }

    /**
     * 홈의 최신 맛집 (FUNCTION_SPEC F1-08). 최근 등록 순으로 count개(1~50)만 읽는다. 전체 맛집을 읽지 않는다.
     * 맛집 수와 상관없이 쿼리 수가 일정하다. 맛집·카테고리는 한 번에, 대표 이미지는 한 번의 IN 쿼리로 읽는다.
     */
    @Transactional(readOnly = true)
    public List<RestaurantSummary> latest(int count) {
        int limit = Math.min(Math.max(count, 1), SIZE_MAX);
        return summaries(restaurants.findByOrderByCreatedAtDescIdDesc(PageRequest.of(0, limit)));
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
        return detailFor(find(restaurantId), viewerId);
    }

    /**
     * 상세 응답을 만든다. 찜 수는 맛집별 COUNT 한 번, 찜 여부는 회원·맛집 쌍 조회 한 번이다.
     * 목록이 아니라 한 맛집에만 쓰므로 맛집 수에 비례하는 추가 조회가 생기지 않는다.
     */
    private RestaurantDetail detailFor(Restaurant restaurant, Long viewerId) {
        long id = restaurant.getId();
        boolean isOwner = viewerId != null && restaurant.isOwnedBy(viewerId);
        int favoriteCount = (int) favorites.countByRestaurantId(id);
        boolean favorited = viewerId != null && favorites.existsByMemberIdAndRestaurantId(viewerId, id);
        return RestaurantDetail.of(restaurant, isOwner, imagesOf(id), favoriteCount, favorited);
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
        return updateRestaurant(restaurantId, memberId, request);
    }

    /**
     * 관리자 맛집 수정 (FUNCTION_SPEC F1-09). 작성자 확인만 생략하고, 입력 검증과 갱신은 일반 수정과 같다.
     * 작성자(member)는 바뀌지 않는다. 요청 형식에 작성자 필드가 없고, 알 수 없는 필드는 400이다.
     */
    @Transactional
    public RestaurantDetail updateAsAdmin(long restaurantId, Authentication actor, RestaurantRequest request) {
        AdminAccess.requireAdmin(actor);
        return updateRestaurant(restaurantId, null, request);
    }

    private RestaurantDetail updateRestaurant(long restaurantId, Long ownerOrNull, RestaurantRequest request) {
        Restaurant restaurant = find(restaurantId);
        if (ownerOrNull != null) {
            requireOwner(restaurant, ownerOrNull);
        }
        RestaurantInputRules.Validated input = RestaurantInputRules.validate(request);
        Category category = findCategory(input.categoryId());
        restaurant.update(category, input.fields());
        return detailFor(restaurant, ownerOrNull);
    }

    /**
     * 삭제 (API_SPEC 4.5). 작성자 본인만 할 수 있다.
     *
     * 1) DB 트랜잭션: 맛집 이미지마다 storage_delete_task에 키를 기록하고, 이미지 행과 맛집 행을 지운다. 커밋한다.
     *    이 단계가 실패하면 S3에는 아무 일도 하지 않는다.
     * 2) 커밋 뒤: 각 키의 S3 객체를 지운다. 성공하면 추적 행을 지우고, 실패하면 추적 행을 남긴다.
     *    S3 실패는 맛집 삭제를 되돌리지 않는다. 응답의 storageDeleteFailedCount로 알린다.
     *
     * 찜, 리뷰, AI 요약 행은 같은 트랜잭션에서 지운다 (DATABASE_ERD 삭제 흐름). FK CASCADE는 안전장치다.
     */
    public DeleteResult delete(long restaurantId, long memberId) {
        return deleteRestaurant(restaurantId, memberId);
    }

    /** 관리자 맛집 삭제 (FUNCTION_SPEC F1-09). 작성자 확인만 생략하고 나머지 흐름은 delete와 같다. */
    public DeleteResult deleteAsAdmin(long restaurantId, Authentication actor) {
        AdminAccess.requireAdmin(actor);
        return deleteRestaurant(restaurantId, null);
    }

    private DeleteResult deleteRestaurant(long restaurantId, Long ownerOrNull) {
        List<String> s3Keys = tx.execute(status -> {
            // 맛집 행을 먼저 잠근다. 같은 맛집에 대한 이미지 업로드·개별 삭제는 이 잠금이 풀릴 때까지 기다린다.
            // 잠금을 잡은 뒤에 이미지 목록을 읽어야, 커밋되지 않은 업로드가 목록에서 빠지지 않는다.
            Restaurant restaurant = findForUpdate(restaurantId);
            if (ownerOrNull != null) {
                requireOwner(restaurant, ownerOrNull);
            }
            List<RestaurantImage> owned = images.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId);
            List<String> keys = new ArrayList<>();
            for (RestaurantImage image : owned) {
                storageCleanup.recordPending(image.getS3Key());
                keys.add(image.getS3Key());
            }
            images.deleteAll(owned);
            // 찜은 리뷰보다 먼저 지운다 (F1-06, DATABASE_ERD 4.x 삭제 흐름). 맛집 행이 사라지면 CASCADE가 남은 찜도 지운다.
            favorites.deleteAll(favorites.findByRestaurantId(restaurantId));
            // 리뷰는 같은 트랜잭션에서 지운다. 맛집 행이 사라지면 ON DELETE CASCADE가 남은 리뷰도 지우지만, 순서를 명시한다.
            reviews.deleteAll(reviews.findByRestaurantId(restaurantId));
            // AI 요약 캐시도 같은 트랜잭션에서 지운다 (F1-02 삭제 흐름, F1-05). FK CASCADE는 안전장치다.
            summaries.deleteByRestaurantId(restaurantId);
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

    /** 관리자 수정 화면에 채울 값. 작성자 확인은 하지 않는다. 관리자만 쓴다. */
    @Transactional(readOnly = true)
    public RestaurantDetail loadForEditAsAdmin(long restaurantId, Authentication actor) {
        AdminAccess.requireAdmin(actor);
        return detailFor(find(restaurantId), null);
    }

    /** 수정 화면에 채울 값. 작성자가 아니면 FORBIDDEN이다. */
    @Transactional(readOnly = true)
    public RestaurantDetail loadForEdit(long restaurantId, long memberId) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        return detailFor(restaurant, memberId);
    }

    /**
     * 맛집 목록 항목으로 바꾼다. 대표 이미지는 맛집 수와 상관없이 한 번의 쿼리로 모은다.
     * 찜 목록처럼 다른 기능이 맛집 목록을 만들 때 쓴다. 호출하는 쪽의 트랜잭션 안에서 부른다.
     */
    public List<RestaurantSummary> summaries(List<Restaurant> items) {
        Map<Long, String> thumbnails = thumbnailsOf(items.stream().map(Restaurant::getId).toList());
        return items.stream().map(r -> RestaurantSummary.from(r, thumbnails.get(r.getId()))).toList();
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

    /** 목록 정렬 규칙. latest(기본)와 rating만 허용한다. 관리자 목록도 같은 규칙을 쓴다. */
    public static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank() || "latest".equals(sort)) {
            return LATEST;
        }
        if ("rating".equals(sort)) {
            return BY_RATING;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
}
