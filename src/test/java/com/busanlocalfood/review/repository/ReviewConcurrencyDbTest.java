package com.busanlocalfood.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.service.ReviewService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰의 동시성 검증 (실제 MySQL 8, SQL 001~006). 확인하는 것: 같은 회원의 중복 작성은 한 번만 성공하는지,
 * 서로 다른 작성·삭제가 겹쳐도 맛집의 평균과 리뷰 수가 리뷰 행과 맞는지, 맛집 삭제와 작성이 겹쳐도 고아 리뷰가 없는지.
 * 스레드 타이밍은 무작위지만 검사하는 불변식은 어떤 순서에서도 같아야 한다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=review-concurrency-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({ReviewService.class, RestaurantService.class, StorageCleanup.class, ImageUrls.class,
        ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewConcurrencyDbTest {

    private static final int ROUNDS = 20;
    private static final long WAIT_SECONDS = 30;

    @MockitoBean
    private ImageStorage storage;

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
    private List<Member> pool;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("rc-owner@example.com", "hash", "작성자", "rc-sub-owner"));
        pool = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            pool.add(members.save(Member.create("rc-m" + i + "@example.com", "hash", "회원" + i, "rc-sub-" + i)));
        }
        category = categories.findAllByOrderBySortOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM review");
        restaurants.deleteAll();
        members.findAll().stream().filter(m -> m.getEmail().startsWith("rc-")).forEach(members::delete);
    }

    private Long newRestaurant(String name) {
        return restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산", null, "설명", null, null))).getId();
    }

    private static ReviewRequest rating(int value) {
        return new ReviewRequest(BigDecimal.valueOf(value), "동시성 리뷰");
    }

    private static <T> Callable<T> call(Callable<T> body) {
        return body;
    }

    /** 맛집의 저장된 평균과 리뷰 수가 리뷰 행에서 다시 계산한 값과 같아야 한다. 평균은 합계와 개수로 계산한다. */
    private void assertStatisticsMatchRows(Long restaurantId, String context) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", Long.class, restaurantId);
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(rating), 0) FROM review WHERE restaurant_id = ?",
                Long.class, restaurantId);
        BigDecimal expected = count == 0 ? new BigDecimal("0.0")
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        var stored = restaurants.findById(restaurantId);
        assertThat(stored).as(context + ": 맛집").isPresent();
        assertThat(stored.get().getReviewCount()).as(context + ": 리뷰 수").isEqualTo(count.intValue());
        assertThat(stored.get().getAverageRating()).as(context + ": 평균").isEqualByComparingTo(expected);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 서로_다른_회원의_동시_작성은_모두_반영되고_평균과_수가_맞는다() throws Exception {
        Long id = newRestaurant("동시 작성");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CyclicBarrier start = new CyclicBarrier(8);
            List<Future<ReviewCreated>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int index = i;
                futures.add(executor.submit(call(() -> {
                    start.await();
                    return reviewService.create(id, pool.get(index).getId(), rating(index % 5 + 1));
                })));
            }
            for (Future<ReviewCreated> f : futures) {
                await(f);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(restaurants.findById(id).orElseThrow().getReviewCount()).isEqualTo(8);
        // 평점은 1,2,3,4,5,1,2,3 → 합계 21, 평균 2.625 → 2.6
        assertThat(restaurants.findById(id).orElseThrow().getAverageRating()).isEqualByComparingTo("2.6");
        assertStatisticsMatchRows(id, "동시 작성");
    }

    @Test
    void 같은_회원의_동시_중복_작성은_하나만_성공하고_나머지는_REVIEW_ALREADY_EXISTS다() throws Exception {
        Long id = newRestaurant("중복 경합");
        Member same = pool.get(0);
        ExecutorService executor = Executors.newFixedThreadPool(6);
        int success = 0;
        int duplicate = 0;
        try {
            CyclicBarrier start = new CyclicBarrier(6);
            List<Future<ReviewCreated>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(call(() -> {
                    start.await();
                    return reviewService.create(id, same.getId(), rating(4));
                })));
            }
            for (Future<ReviewCreated> f : futures) {
                try {
                    await(f);
                    success++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof BusinessException be
                            && be.getErrorCode() == ErrorCode.REVIEW_ALREADY_EXISTS) {
                        duplicate++;
                    } else {
                        throw e;
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(success).isEqualTo(1);
        assertThat(duplicate).isEqualTo(5);
        assertStatisticsMatchRows(id, "중복 경합");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void 리뷰_작성과_리뷰_삭제가_겹쳐도_평균과_리뷰_수가_남은_리뷰와_맞는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long id = newRestaurant("작성 삭제 경합 " + round);
            ReviewCreated existing = reviewService.create(id, pool.get(0).getId(), rating(2));
            reviewService.create(id, pool.get(1).getId(), rating(5));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> deleting = executor.submit(call(() -> {
                    start.await();
                    return reviewService.delete(existing.id(), pool.get(0).getId());
                }));
                Future<?> creating = executor.submit(call(() -> {
                    start.await();
                    return reviewService.create(id, pool.get(2).getId(), rating(4));
                }));
                await(deleting);
                await(creating);
            } finally {
                executor.shutdownNow();
            }
            assertStatisticsMatchRows(id, "작성 삭제 경합 round " + round);
        }
    }

    @Test
    void 맛집_삭제와_리뷰_작성이_겹쳐도_고아_리뷰가_남지_않고_맛집_값은_일관된다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long id = newRestaurant("맛집 삭제 경합 " + round);
            reviewService.create(id, pool.get(0).getId(), rating(3));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> creating = executor.submit(call(() -> {
                    start.await();
                    return reviewService.create(id, pool.get(1).getId(), rating(5));
                }));
                Future<?> deleting = executor.submit(call(() -> {
                    start.await();
                    return restaurantService.delete(id, owner.getId());
                }));
                // 작성은 맛집이 먼저 지워졌으면 RESTAURANT_NOT_FOUND가 될 수 있다. 그 밖의 실패는 테스트 실패다.
                try {
                    await(creating);
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof BusinessException be)
                            || be.getErrorCode() != ErrorCode.RESTAURANT_NOT_FOUND) {
                        throw e;
                    }
                }
                await(deleting);
            } finally {
                executor.shutdownNow();
            }

            if (restaurants.findById(id).isEmpty()) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", Long.class, id))
                        .as("맛집 삭제 경합 round " + round + ": 고아 리뷰").isZero();
            } else {
                assertStatisticsMatchRows(id, "맛집 삭제 경합 round " + round);
            }
        }
    }
}
