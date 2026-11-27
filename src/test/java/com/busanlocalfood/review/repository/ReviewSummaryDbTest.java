package com.busanlocalfood.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.ai.config.AiConfig;
import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiUnavailableException;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse;
import com.busanlocalfood.review.service.ReviewService;
import com.busanlocalfood.review.service.ReviewSummaryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 리뷰 요약 캐시의 MySQL 동작 (SQL 001~007). 외부 AI는 목이다. 확인: 3개 미만은 호출 안 함, 캐시 재사용,
 * 리뷰 작성·삭제 시 무효화, AI 실패와 잘못된 응답은 캐시하지 않음, 동시 요청은 한 번만 생성,
 * 생성 중 리뷰가 바뀌면 캐시하지 않음, 무효화 실패 시 리뷰도 롤백, 맛집 삭제 연동, 테이블 제약.
 * 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.ai.openrouter.base-url=https://openrouter.ai/api/v1",
        "app.ai.openrouter.api-key=test-only-key",
        "app.ai.openrouter.model=test/model",
        "app.ai.openrouter.timeout-seconds=10"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({ReviewService.class, ReviewSummaryService.class, AiConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReviewSummaryDbTest {

    private static final String SUMMARY = "맛이 진하고 좋아요. 분위기도 편안합니다. 가족 모임에 추천합니다.";

    /** 외부 AI 호출. 실제 OpenRouter에 접속하지 않는다. */
    @MockitoBean
    private ChatCompletionClient chat;

    /** 무효화 실패 시나리오에서만 실패하게 한다. */
    @MockitoSpyBean
    private ReviewSummaryRepository summaryRepository;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewSummaryService summaryService;

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
    private Member dave;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("rs-owner@example.com", "hash", "작성자", "rs-sub-owner"));
        alice = members.save(Member.create("rs-alice@example.com", "hash", "앨리스", "rs-sub-alice"));
        bob = members.save(Member.create("rs-bob@example.com", "hash", "밥", "rs-sub-bob"));
        carol = members.save(Member.create("rs-carol@example.com", "hash", "캐롤", "rs-sub-carol"));
        dave = members.save(Member.create("rs-dave@example.com", "hash", "데이브", "rs-sub-dave"));
        category = categories.findAllByOrderBySortOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM ai_review_summary");
        jdbc.update("DELETE FROM review");
        restaurants.deleteAll();
        members.findAll().stream().filter(m -> m.getEmail().startsWith("rs-")).forEach(members::delete);
    }

    private Long restaurantWithReviews(String name, int reviewCount) {
        Long id = restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산", null, "설명", null, null))).getId();
        List<Member> writers = List.of(alice, bob, carol, dave);
        for (int i = 0; i < reviewCount; i++) {
            reviewService.create(id, writers.get(i).getId(), new ReviewRequest(BigDecimal.valueOf(4), "리뷰 " + i));
        }
        return id;
    }

    private long summaryRows(Long restaurantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ai_review_summary WHERE restaurant_id = ?", Long.class, restaurantId);
        return count == null ? 0 : count;
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("래치가 풀리지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 리뷰가_두_개면_AI를_부르지_않고_안내_문구를_돌려준다() {
        Long id = restaurantWithReviews("두 개", 2);

        SummaryResponse view = summaryService.summaryFor(id);

        assertThat(view.status()).isEqualTo("NOT_ENOUGH_REVIEWS");
        assertThat(view.summary()).isNull();
        assertThat(view.reviewCount()).isEqualTo(2);
        assertThat(view.message()).contains("리뷰 3개 이상");
        verify(chat, never()).complete(anyList());
    }

    @Test
    void 리뷰가_세_개면_한_번_생성해_저장하고_다음부터는_캐시를_쓴다() {
        Long id = restaurantWithReviews("세 개", 3);
        when(chat.complete(anyList())).thenReturn(SUMMARY);

        SummaryResponse first = summaryService.summaryFor(id);
        SummaryResponse second = summaryService.summaryFor(id);

        assertThat(first.status()).isEqualTo("READY");
        assertThat(first.summary()).isEqualTo(SUMMARY);
        assertThat(first.cached()).isFalse();
        assertThat(second.status()).isEqualTo("READY");
        assertThat(second.cached()).isTrue();
        assertThat(second.reviewCount()).isEqualTo(3);
        assertThat(summaryRows(id)).isEqualTo(1);
        verify(chat, times(1)).complete(anyList());
    }

    @Test
    void 리뷰를_작성하면_캐시가_지워지고_다음_요청에서_다시_생성한다() {
        Long id = restaurantWithReviews("작성 무효화", 3);
        when(chat.complete(anyList())).thenReturn(SUMMARY);
        summaryService.summaryFor(id);
        assertThat(summaryRows(id)).isEqualTo(1);

        reviewService.create(id, dave.getId(), new ReviewRequest(BigDecimal.valueOf(5), "새 리뷰"));

        assertThat(summaryRows(id)).isZero();
        SummaryResponse again = summaryService.summaryFor(id);
        assertThat(again.cached()).isFalse();
        assertThat(again.reviewCount()).isEqualTo(4);
        verify(chat, times(2)).complete(anyList());
    }

    @Test
    void 리뷰를_삭제하면_캐시가_지워진다() {
        Long id = restaurantWithReviews("삭제 무효화", 4);
        when(chat.complete(anyList())).thenReturn(SUMMARY);
        summaryService.summaryFor(id);
        Long oneReview = jdbc.queryForObject("SELECT MIN(id) FROM review WHERE restaurant_id = ?", Long.class, id);

        reviewService.delete(oneReview, alice.getId());

        assertThat(summaryRows(id)).isZero();
    }

    @Test
    void AI_호출이_실패하면_UNAVAILABLE이고_캐시하지_않으며_다음_요청에서_다시_시도한다() {
        Long id = restaurantWithReviews("AI 실패", 3);
        // 예외를 던지는 스텁을 when(...)으로 다시 설정하면 그 호출이 예외를 던지므로 doThrow를 쓴다.
        doThrow(new AiUnavailableException("연결 실패")).when(chat).complete(anyList());

        SummaryResponse failed = summaryService.summaryFor(id);

        assertThat(failed.status()).isEqualTo("UNAVAILABLE");
        assertThat(failed.summary()).isNull();
        assertThat(failed.message()).contains("요약을 불러올 수 없습니다");
        assertThat(summaryRows(id)).isZero();

        doReturn(SUMMARY).when(chat).complete(anyList());
        SummaryResponse recovered = summaryService.summaryFor(id);
        assertThat(recovered.status()).isEqualTo("READY");
        assertThat(recovered.cached()).isFalse();
    }

    @Test
    void 빈_응답과_지나치게_긴_응답은_잘못된_응답으로_보고_캐시하지_않는다() {
        Long id = restaurantWithReviews("잘못된 응답", 3);

        when(chat.complete(anyList())).thenReturn("   ");
        assertThat(summaryService.summaryFor(id).status()).isEqualTo("UNAVAILABLE");

        when(chat.complete(anyList())).thenReturn("가".repeat(1001));
        assertThat(summaryService.summaryFor(id).status()).isEqualTo("UNAVAILABLE");

        assertThat(summaryRows(id)).isZero();
    }

    @Test
    void 같은_맛집의_동시_요청은_AI를_한_번만_부른다() throws Exception {
        Long id = restaurantWithReviews("동시 요청", 3);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(chat.complete(anyList())).thenAnswer(inv -> {
            calls.incrementAndGet();
            entered.countDown();
            awaitLatch(release);
            return SUMMARY;
        });

        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            CyclicBarrier start = new CyclicBarrier(5);
            List<Future<SummaryResponse>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return summaryService.summaryFor(id);
                }));
            }
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (Future<SummaryResponse> f : results) {
                assertThat(f.get(20, TimeUnit.SECONDS).status()).isEqualTo("READY");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(calls.get()).isEqualTo(1);
        assertThat(summaryRows(id)).isEqualTo(1);
    }

    @Test
    void 생성_중에_리뷰가_바뀌면_결과를_캐시하지_않는다() throws Exception {
        Long id = restaurantWithReviews("생성 중 변경", 3);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(chat.complete(anyList())).thenAnswer(inv -> {
            entered.countDown();
            awaitLatch(release);
            return SUMMARY;
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<SummaryResponse> generating = pool.submit(() -> summaryService.summaryFor(id));
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();

            // 생성 도중 리뷰가 추가된다. 저장 단계는 이 변화를 보고 결과를 버려야 한다.
            reviewService.create(id, dave.getId(), new ReviewRequest(BigDecimal.valueOf(2), "중간 리뷰"));
            release.countDown();

            SummaryResponse result = generating.get(20, TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo("READY");
            assertThat(result.cached()).isFalse();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(summaryRows(id)).isZero();
        assertThat(summaryService.summaryFor(id).reviewCount()).isEqualTo(4);
    }

    @Test
    void 리뷰_작성의_캐시_무효화가_실패하면_리뷰와_맛집_집계도_함께_롤백된다() {
        Long id = restaurantWithReviews("무효화 롤백", 2);
        doThrow(new IllegalStateException("주입된 실패")).when(summaryRepository).deleteByRestaurantId(anyLong());

        assertThatThrownBy(() -> reviewService.create(id, carol.getId(), new ReviewRequest(BigDecimal.valueOf(5), "롤백")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", Long.class, id)).isEqualTo(2L);
        assertThat(restaurants.findById(id).orElseThrow().getReviewCount()).isEqualTo(2);
    }

    @Test
    void 맛집이_없으면_RESTAURANT_NOT_FOUND다() {
        assertThatThrownBy(() -> summaryService.summaryFor(999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    void 맛집을_삭제하면_요약_캐시도_함께_지워진다() {
        Long id = restaurantWithReviews("맛집 삭제", 3);
        when(chat.complete(anyList())).thenReturn(SUMMARY);
        summaryService.summaryFor(id);
        assertThat(summaryRows(id)).isEqualTo(1);

        restaurants.deleteById(id);

        assertThat(summaryRows(id)).isZero();
    }

    @Test
    void 캐시_테이블은_맛집당_한_행이고_리뷰_수_3_미만을_받지_않는다() {
        Long id = restaurantWithReviews("제약", 3);
        jdbc.update("INSERT INTO ai_review_summary (restaurant_id, summary, source_review_count) VALUES (?, ?, 3)", id, SUMMARY);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ai_review_summary (restaurant_id, summary, source_review_count) VALUES (?, ?, 3)", id, SUMMARY))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long other = restaurantWithReviews("제약2", 0);
        // CHECK 위반은 JdbcTemplate가 DataAccessException으로 감싼다. 어느 제약이 막았는지 메시지로 확인한다.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ai_review_summary (restaurant_id, summary, source_review_count) VALUES (?, ?, 2)", other, SUMMARY))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_summary_review_count");
    }
}
