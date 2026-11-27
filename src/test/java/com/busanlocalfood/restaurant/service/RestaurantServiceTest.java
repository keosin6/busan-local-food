package com.busanlocalfood.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.image.repository.RestaurantImageRepository;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** 맛집 서비스의 작성자 확인, 없는 맛집, 없는 카테고리 분기. 리포지토리는 목이다. DB 없이 실행한다. */
class RestaurantServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long RESTAURANT_ID = 10L;
    private static final long CATEGORY_ID = 3L;

    private RestaurantRepository restaurants;
    private CategoryRepository categories;
    private MemberRepository members;
    private RestaurantImageRepository images;
    private StorageCleanup storageCleanup;
    private ImageUrls imageUrls;
    private RestaurantService service;

    private Restaurant stored;
    private Category category;

    @BeforeEach
    void setUp() {
        restaurants = mock(RestaurantRepository.class);
        categories = mock(CategoryRepository.class);
        members = mock(MemberRepository.class);
        images = mock(RestaurantImageRepository.class);
        storageCleanup = mock(StorageCleanup.class);
        imageUrls = mock(ImageUrls.class);
        service = new RestaurantService(restaurants, categories, members, images,
                mock(com.busanlocalfood.review.repository.ReviewRepository.class),
                mock(com.busanlocalfood.review.repository.ReviewSummaryRepository.class), storageCleanup, imageUrls,
                mock(PlatformTransactionManager.class));

        category = mock(Category.class);
        Member owner = Member.create("owner@example.com", "hash", "작성자", "google-sub-owner");
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        stored = Restaurant.create(owner, category, fields("기존 이름"));
        ReflectionTestUtils.setField(stored, "id", RESTAURANT_ID);

        when(restaurants.findById(RESTAURANT_ID)).thenReturn(Optional.of(stored));
        // 삭제는 맛집 행을 잠근 뒤 권한을 본다 (findByIdForUpdate).
        when(restaurants.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.of(stored));
        when(categories.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
    }

    private static RestaurantRequest request(long categoryId) {
        return new RestaurantRequest("새 이름", categoryId, "부산 해운대구", null, "설명",
                new BigDecimal("35.1587000"), new BigDecimal("129.1604000"));
    }

    private static com.busanlocalfood.restaurant.domain.Restaurant.RestaurantFields fields(String name) {
        return new Restaurant.RestaurantFields(name, "부산", null, "설명", null, null);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 작성자가_아니면_수정은_FORBIDDEN이고_저장하지_않는다() {
        assertCode(() -> service.update(RESTAURANT_ID, OTHER_ID, request(CATEGORY_ID)), ErrorCode.FORBIDDEN);
        verify(restaurants, never()).save(any());
        assertThat(stored.getName()).isEqualTo("기존 이름");
    }

    @Test
    void 작성자가_아니면_삭제는_FORBIDDEN이고_지우지_않는다() {
        assertCode(() -> service.delete(RESTAURANT_ID, OTHER_ID), ErrorCode.FORBIDDEN);
        verify(restaurants, never()).delete(any(Restaurant.class));
    }

    @Test
    void 작성자가_아니면_수정_화면_값도_FORBIDDEN이다() {
        assertCode(() -> service.loadForEdit(RESTAURANT_ID, OTHER_ID), ErrorCode.FORBIDDEN);
    }

    @Test
    void 작성자는_수정하면_새_값이_반영된다() {
        RestaurantDetail detail = service.update(RESTAURANT_ID, OWNER_ID, request(CATEGORY_ID));

        assertThat(detail.name()).isEqualTo("새 이름");
        assertThat(detail.isOwner()).isTrue();
        assertThat(stored.getName()).isEqualTo("새 이름");
    }

    @Test
    void 작성자는_삭제하면_삭제_결과에_이미지_수_0을_돌려준다() {
        DeleteResult result = service.delete(RESTAURANT_ID, OWNER_ID);

        assertThat(result.restaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(result.deletedImageCount()).isZero();
        assertThat(result.storageDeleteFailedCount()).isZero();
        verify(restaurants).delete(stored);
    }

    @Test
    void 없는_맛집은_상세_수정_삭제_수정화면_모두_RESTAURANT_NOT_FOUND다() {
        when(restaurants.findById(99L)).thenReturn(Optional.empty());

        assertCode(() -> service.detail(99L, null), ErrorCode.RESTAURANT_NOT_FOUND);
        assertCode(() -> service.update(99L, OWNER_ID, request(CATEGORY_ID)), ErrorCode.RESTAURANT_NOT_FOUND);
        assertCode(() -> service.delete(99L, OWNER_ID), ErrorCode.RESTAURANT_NOT_FOUND);
        assertCode(() -> service.loadForEdit(99L, OWNER_ID), ErrorCode.RESTAURANT_NOT_FOUND);
    }

    @Test
    void 비회원_상세는_isOwner가_false다() {
        assertThat(service.detail(RESTAURANT_ID, null).isOwner()).isFalse();
        assertThat(service.detail(RESTAURANT_ID, OTHER_ID).isOwner()).isFalse();
        assertThat(service.detail(RESTAURANT_ID, OWNER_ID).isOwner()).isTrue();
    }

    @Test
    void 없는_카테고리로_등록하면_CATEGORY_NOT_FOUND다() {
        Member member = Member.create("m@example.com", "hash", "회원", "google-sub-member");
        ReflectionTestUtils.setField(member, "id", OTHER_ID);
        when(members.findById(OTHER_ID)).thenReturn(Optional.of(member));
        when(categories.findById(77L)).thenReturn(Optional.empty());

        assertCode(() -> service.create(OTHER_ID, request(77L)), ErrorCode.CATEGORY_NOT_FOUND);
        verify(restaurants, never()).save(any());
    }

    @Test
    void 회원이_없으면_등록은_TOKEN_INVALID다() {
        when(members.findById(OTHER_ID)).thenReturn(Optional.empty());

        assertCode(() -> service.create(OTHER_ID, request(CATEGORY_ID)), ErrorCode.TOKEN_INVALID);
    }

    @Test
    void 알_수_없는_정렬은_VALIDATION_FAILED다() {
        assertCode(() -> service.list(null, null, "popular", 0, 12), ErrorCode.VALIDATION_FAILED);
    }
}
