package com.busanlocalfood.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.domain.Review;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewDeleteResult;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewItem;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.service.ReviewService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰의 MySQL 동작 (SQL 001~006 적용 후). 작성·중복·UNIQUE 제약, 목록, 삭제 권한, 평균 평점과 리뷰 수 갱신,
 * 롤백 일관성, 맛집 삭제 시 리뷰 정리를 확인한다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=review-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({ReviewService.class, RestaurantService.class, StorageCleanup.class, ImageUrls.class,
        ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewDbTest {

    @MockitoBean
    private ImageStorage storage;

    /** 롤백 테스트에서만 집계 쿼리를 실패시킨다. 나머지는 실제 저장소 동작 그대로다. */
    @MockitoSpyBean
    private ReviewRepository reviews;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Member alice;
    private Member bob;
    private Member carol;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("rev-owner@example.com", "hash", "작성자", "rev-sub-owner"));
        alice = members.save(Member.create("rev-alice@example.com", "hash", "앨리스", "rev-sub-alice"));
        bob = members.save(Member.create("rev-bob@example.com", "hash", "밥", "rev-sub-bob"));
        carol = members.save(Member.create("rev-carol@example.com", "hash", "캐롤", "rev-sub-carol"));
        category = categories.findAllByOrderBySortOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM storage_delete_task");
        jdbc.update("DELETE FROM review");
        jdbc.update("DELETE FROM restaurant_image");
        restaurants.deleteAll();
        members.findAll().stream().filter(m -> m.getEmail().startsWith("rev-")).forEach(members::delete);
    }

    private Long newRestaurant(String name) {
        return restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산 해운대구", null, "설명", null, null))).getId();
    }

    private static ReviewRequest rating(String value) {
        return new ReviewRequest(new BigDecimal(value), "리뷰 내용");
    }

    private Restaurant reload(Long id) {
        return restaurants.findById(id).orElseThrow();
    }

    private long reviewRows(Long restaurantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", Long.class, restaurantId);
        return count == null ? 0 : count;
    }

    @Test
    void 평점_1점과_5점은_저장되고_평균과_리뷰_수가_맞게_갱신된다() {
        Long id = newRestaurant("평점 맛집");

        ReviewCreated first = reviewService.create(id, alice.getId(), rating("1"));
        assertThat(first.rating()).isEqualTo(1);
        assertThat(first.restaurantReviewCount()).isEqualTo(1);
        assertThat(first.restaurantAverageRating()).isEqualByComparingTo("1.0");

        ReviewCreated second = reviewService.create(id, bob.getId(), rating("5"));
        assertThat(second.restaurantReviewCount()).isEqualTo(2);
        assertThat(second.restaurantAverageRating()).isEqualByComparingTo("3.0");
        assertThat(reload(id).getReviewCount()).isEqualTo(2);
        assertThat(reload(id).getAverageRating()).isEqualByComparingTo("3.0");
    }

    @Test
    void 평점_0점과_6점과_소수_평점은_저장되지_않는다() {
        Long id = newRestaurant("거부 맛집");

        assertInvalid(() -> reviewService.create(id, alice.getId(), rating("0")));
        assertInvalid(() -> reviewService.create(id, alice.getId(), rating("6")));
        assertInvalid(() -> reviewService.create(id, alice.getId(), rating("4.5")));
        assertThat(reviewRows(id)).isZero();
    }

    @Test
    void 없는_맛집에는_리뷰를_쓸_수_없다() {
        assertThatThrownBy(() -> reviewService.create(999_999L, alice.getId(), rating("4")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    void 같은_회원의_중복_작성은_거부되고_다른_회원은_허용된다() {
        Long id = newRestaurant("중복 맛집");
        reviewService.create(id, alice.getId(), rating("4"));

        assertThatThrownBy(() -> reviewService.create(id, alice.getId(), rating("2")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS));
        reviewService.create(id, bob.getId(), rating("2"));

        assertThat(reviewRows(id)).isEqualTo(2);
    }

    @Test
    void 데이터베이스_UNIQUE_제약도_같은_회원의_중복_리뷰를_막는다() {
        Long id = newRestaurant("제약 맛집");
        reviewService.create(id, alice.getId(), rating("4"));
        Restaurant restaurant = reload(id);

        assertThatThrownBy(() -> reviews.saveAndFlush(Review.create(restaurant, alice, 3, "직접 저장")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 목록은_최신순이고_작성자_닉네임과_본인_여부를_준다() {
        Long id = newRestaurant("목록 맛집");
        reviewService.create(id, alice.getId(), rating("3"));
        reviewService.create(id, bob.getId(), rating("4"));
        reviewService.create(id, carol.getId(), rating("5"));

        PageResponse<ReviewItem> page = reviewService.list(id, bob.getId(), 0, 10);

        assertThat(page.content()).extracting(r -> r.author().nickname()).containsExactly("캐롤", "밥", "앨리스");
        assertThat(page.content()).extracting(ReviewItem::isMine).containsExactly(false, true, false);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).author().memberId()).isEqualTo(carol.getId());
    }

    @Test
    void 목록_페이지는_전체_개수와_마지막_페이지를_맞게_계산한다() {
        Long id = newRestaurant("페이지 맛집");
        reviewService.create(id, alice.getId(), rating("3"));
        reviewService.create(id, bob.getId(), rating("4"));
        reviewService.create(id, carol.getId(), rating("5"));

        PageResponse<ReviewItem> second = reviewService.list(id, null, 1, 2);

        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.content()).hasSize(1);
        assertThat(second.content().get(0).isMine()).isFalse();
    }

    @Test
    void 평균은_소수_첫째_자리에서_반올림되어_저장된다() {
        Long id = newRestaurant("반올림 맛집");
        reviewService.create(id, alice.getId(), rating("5"));
        reviewService.create(id, bob.getId(), rating("4"));
        ReviewCreated third = reviewService.create(id, carol.getId(), rating("4"));

        assertThat(third.restaurantAverageRating()).isEqualByComparingTo("4.3");
        assertThat(reload(id).getAverageRating()).isEqualByComparingTo("4.3");
    }

    @Test
    void 본인_리뷰를_지우면_평균과_리뷰_수가_남은_리뷰로_다시_계산된다() {
        Long id = newRestaurant("삭제 맛집");
        ReviewCreated mine = reviewService.create(id, alice.getId(), rating("1"));
        reviewService.create(id, bob.getId(), rating("5"));

        var deleted = reviewService.delete(mine.id(), alice.getId());

        assertThat(deleted.restaurantId()).isEqualTo(id);
        ReviewDeleteResult result = deleted.result();
        assertThat(result.restaurantReviewCount()).isEqualTo(1);
        assertThat(result.restaurantAverageRating()).isEqualByComparingTo("5.0");
        assertThat(reload(id).getReviewCount()).isEqualTo(1);
        assertThat(reload(id).getAverageRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void 마지막_리뷰를_지우면_평균은_0점0이고_리뷰_수는_0이다() {
        Long id = newRestaurant("마지막 맛집");
        ReviewCreated only = reviewService.create(id, alice.getId(), rating("4"));

        ReviewDeleteResult result = reviewService.delete(only.id(), alice.getId()).result();

        assertThat(result.restaurantReviewCount()).isZero();
        assertThat(result.restaurantAverageRating()).isEqualByComparingTo("0.0");
        assertThat(reload(id).getAverageRating()).isEqualByComparingTo("0.0");
        assertThat(reload(id).getReviewCount()).isZero();
    }

    @Test
    void 타인의_리뷰는_삭제할_수_없고_행도_남는다() {
        Long id = newRestaurant("타인 맛집");
        ReviewCreated theirs = reviewService.create(id, alice.getId(), rating("4"));

        assertThatThrownBy(() -> reviewService.delete(theirs.id(), bob.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(reviewRows(id)).isEqualTo(1);
        assertThat(reload(id).getReviewCount()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_리뷰_삭제는_REVIEW_NOT_FOUND다() {
        assertThatThrownBy(() -> reviewService.delete(999_999L, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND));
    }

    @Test
    void 집계_단계가_실패하면_리뷰_저장도_맛집_값_변경도_모두_롤백된다() {
        Long id = newRestaurant("롤백 맛집");
        reviewService.create(id, alice.getId(), rating("5"));
        doThrow(new IllegalStateException("주입된 실패")).when(reviews).sumRatingByRestaurantId(anyLong());

        assertThatThrownBy(() -> reviewService.create(id, bob.getId(), rating("1")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(reviewRows(id)).isEqualTo(1);
        assertThat(reload(id).getReviewCount()).isEqualTo(1);
        assertThat(reload(id).getAverageRating()).isEqualByComparingTo("5.0");
    }

    @Test
    void 맛집을_삭제하면_그_리뷰도_함께_지워져_고아_데이터가_남지_않는다() {
        Long id = newRestaurant("삭제 연동");
        reviewService.create(id, alice.getId(), rating("3"));
        reviewService.create(id, bob.getId(), rating("4"));

        DeleteResult result = restaurantService.delete(id, owner.getId());

        assertThat(result.restaurantId()).isEqualTo(id);
        assertThat(restaurants.findById(id)).isEmpty();
        assertThat(reviewRows(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review", Long.class)).isZero();
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

}
