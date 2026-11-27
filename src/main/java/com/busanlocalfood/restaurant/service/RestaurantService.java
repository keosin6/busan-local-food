package com.busanlocalfood.restaurant.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.repository.RestaurantSearch;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 맛집 관리 서비스 (FUNCTION_SPEC F1-02). 작성자 확인은 여기서 한다. 화면과 API 컨트롤러는 이 결과만 쓴다.
 *
 * 수정과 삭제는 작성자 본인만 허용한다. 관리자는 이 일반 회원 흐름에서 작성자 검사를 우회하지 않는다 (F1-09의 관리자 API를 쓴다).
 * 삭제는 DB 행을 완전히 지운다. 소프트 삭제는 쓰지 않는다.
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

    public RestaurantService(RestaurantRepository restaurants, CategoryRepository categories, MemberRepository members) {
        this.restaurants = restaurants;
        this.categories = categories;
        this.members = members;
    }

    /** 카테고리 목록 (API_SPEC 3.1). 누구나 조회한다. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::from).toList();
    }

    /** 맛집 목록과 검색 (API_SPEC 4.1). 비회원도 조회한다. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummary> list(Long categoryId, String rawKeyword, String sort, int page, int size) {
        String keyword = RestaurantInputRules.keyword(rawKeyword);
        int safePage = RestaurantInputRules.page(page);
        int safeSize = RestaurantInputRules.size(size);
        Sort order = parseSort(sort);
        Page<Restaurant> result = restaurants.findAll(RestaurantSearch.of(categoryId, keyword),
                PageRequest.of(safePage, safeSize, order));
        return new PageResponse<>(result.map(RestaurantSummary::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** 맛집 상세 (API_SPEC 4.2). viewerId가 null이면 비회원이다. */
    @Transactional(readOnly = true)
    public RestaurantDetail detail(long restaurantId, Long viewerId) {
        Restaurant restaurant = find(restaurantId);
        return RestaurantDetail.of(restaurant, viewerId != null && restaurant.isOwnedBy(viewerId));
    }

    /** 등록 (API_SPEC 4.3). 작성자는 현재 인증된 회원이다. */
    @Transactional
    public RestaurantDetail create(long memberId, RestaurantRequest request) {
        Member member = members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        RestaurantInputRules.Validated input = RestaurantInputRules.validate(request);
        Category category = findCategory(input.categoryId());
        Restaurant saved = restaurants.save(Restaurant.create(member, category, input.fields()));
        return RestaurantDetail.of(saved, true);
    }

    /** 수정 (API_SPEC 4.4). 작성자 본인만 할 수 있다. */
    @Transactional
    public RestaurantDetail update(long restaurantId, long memberId, RestaurantRequest request) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        RestaurantInputRules.Validated input = RestaurantInputRules.validate(request);
        Category category = findCategory(input.categoryId());
        restaurant.update(category, input.fields());
        return RestaurantDetail.of(restaurant, true);
    }

    /**
     * 삭제 (API_SPEC 4.5). 작성자 본인만 할 수 있다.
     *
     * 아직 이미지, 리뷰, 찜, AI 요약 테이블이 없으므로 지울 연관 행이 없다. 그래서 반환하는 이미지 수는 0이다.
     * F1-03에서 이 자리에 맛집 이미지의 S3 키 수집과 storage_delete_task 기록을 넣는다.
     * 커밋 뒤의 S3 삭제는 DB 트랜잭션 밖에서 수행하고, 실패 건은 storage_delete_task에 남긴다.
     * F1-04·F1-05·F1-06이 추가되면 리뷰, AI 요약, 찜 행의 삭제도 이 흐름에 넣는다.
     */
    @Transactional
    public DeleteResult delete(long restaurantId, long memberId) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        restaurants.delete(restaurant);
        return new DeleteResult(restaurantId, 0, 0);
    }

    /** 수정 화면에 채울 값. 작성자가 아니면 FORBIDDEN이다. */
    @Transactional(readOnly = true)
    public RestaurantDetail loadForEdit(long restaurantId, long memberId) {
        Restaurant restaurant = find(restaurantId);
        requireOwner(restaurant, memberId);
        return RestaurantDetail.of(restaurant, true);
    }

    private Restaurant find(long restaurantId) {
        return restaurants.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private Category findCategory(long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
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
