package com.busanlocalfood.review.service;

import com.busanlocalfood.ai.config.OpenRouterProperties;
import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiResponseInvalidException;
import com.busanlocalfood.ai.service.ReviewSummaryPrompt;
import com.busanlocalfood.ai.service.ReviewSummaryPrompt.ReviewText;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.review.domain.ReviewSummary;
import com.busanlocalfood.review.dto.ReviewDtos.SummaryResponse;
import com.busanlocalfood.review.repository.ReviewRepository;
import com.busanlocalfood.review.repository.ReviewSummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 리뷰 요약 (FUNCTION_SPEC F1-05, API_SPEC 7.1).
 *
 * 흐름: 리뷰 3개 미만이면 AI를 부르지 않는다 → 캐시가 있으면 반환 → 없으면 OpenRouter를 불러 저장한다.
 * 실패(호출 실패, 제한 시간 초과, 설정 없음, 잘못된 응답)는 예외로 나가지 않고 UNAVAILABLE로 응답한다. 캐시하지 않는다.
 *
 * 중복 생성 방지: 같은 맛집의 동시 요청은 한 번의 생성 작업(CompletableFuture)을 같이 기다린다.
 *   외부 호출은 DB 잠금 밖에서 한다(DEVELOPMENT_RULES 잠금 구간 규칙). 여러 서버 인스턴스 사이의 중복은
 *   ai_review_summary의 UNIQUE 제약과 맛집 행 잠금으로 막는다.
 *
 * 정합성: 저장 직전에 맛집 행을 잠그고, 리뷰 개수와 가장 큰 리뷰 ID가 생성을 시작할 때와 같은지 확인한다.
 *   리뷰 작성·삭제도 같은 맛집 행을 잠근 뒤 캐시를 지우므로, 생성 중 리뷰가 바뀌면 결과를 캐시하지 않는다.
 */
@Service
public class ReviewSummaryService {

    public static final int MIN_REVIEWS = 3;
    static final int MAX_SUMMARY_CHARS = 1000;
    static final String STATUS_READY = "READY";
    static final String STATUS_NOT_ENOUGH = "NOT_ENOUGH_REVIEWS";
    static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    static final String MESSAGE_NOT_ENOUGH = "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다.";
    static final String MESSAGE_UNAVAILABLE = "요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryService.class);
    private static final long WAIT_GRACE_SECONDS = 5;

    private record Fingerprint(long count, long maxId) {
    }

    private final RestaurantRepository restaurants;
    private final ReviewRepository reviews;
    private final ReviewSummaryRepository summaries;
    private final ChatCompletionClient client;
    private final OpenRouterProperties properties;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<Long, CompletableFuture<SummaryResponse>> inFlight = new ConcurrentHashMap<>();

    public ReviewSummaryService(RestaurantRepository restaurants, ReviewRepository reviews,
                                ReviewSummaryRepository summaries, ChatCompletionClient client,
                                OpenRouterProperties properties, PlatformTransactionManager transactionManager) {
        this.restaurants = restaurants;
        this.reviews = reviews;
        this.summaries = summaries;
        this.client = client;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 맛집 요약 조회 (API_SPEC 7.1). 비회원도 조회한다. 맛집이 없으면 RESTAURANT_NOT_FOUND다. */
    public SummaryResponse summaryFor(long restaurantId) {
        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        int count = restaurant.getReviewCount();
        if (count < MIN_REVIEWS) {
            return notEnough(count);
        }
        Optional<ReviewSummary> cached = freshCache(restaurantId, count);
        if (cached.isPresent()) {
            return ready(cached.get(), true);
        }
        if (!properties.configured()) {
            return unavailable(count);
        }
        return generateOnce(restaurantId, count);
    }

    private SummaryResponse generateOnce(long restaurantId, int count) {
        CompletableFuture<SummaryResponse> mine = new CompletableFuture<>();
        CompletableFuture<SummaryResponse> existing = inFlight.putIfAbsent(restaurantId, mine);
        if (existing != null) {
            return awaitOther(existing, count);
        }
        try {
            SummaryResponse view = generateAndStore(restaurantId, count);
            mine.complete(view);
            return view;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(restaurantId, mine);
        }
    }

    /** 이미 진행 중인 생성 작업의 결과를 기다린다. 제한 시간 안에 끝나지 않으면 UNAVAILABLE이다. */
    private SummaryResponse awaitOther(CompletableFuture<SummaryResponse> other, int count) {
        long waitSeconds = properties.timeout().toSeconds() * 2 + WAIT_GRACE_SECONDS;
        try {
            return other.get(waitSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException business) {
                throw business;
            }
            return unavailable(count);
        } catch (TimeoutException e) {
            return unavailable(count);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable(count);
        }
    }

    private SummaryResponse generateAndStore(long restaurantId, int requestCount) {
        // 다른 요청이 방금 저장했을 수 있으므로 한 번 더 확인한다. 이 조회는 잠금을 잡지 않는다.
        Optional<ReviewSummary> cached = freshCache(restaurantId, requestCount);
        if (cached.isPresent()) {
            return ready(cached.get(), true);
        }
        Fingerprint before = fingerprint(restaurantId);
        if (before.count() < MIN_REVIEWS) {
            return notEnough((int) before.count());
        }
        String text;
        try {
            text = validate(client.complete(ReviewSummaryPrompt.build(recentReviews(restaurantId))));
        } catch (RuntimeException e) {
            // 예외 메시지와 리뷰 내용은 남기지 않는다. 예외 종류만 남긴다.
            log.warn("AI 리뷰 요약 생성에 실패했습니다. 예외 종류: {}", e.getClass().getSimpleName());
            return unavailable((int) before.count());
        }
        return tx.execute(status -> store(restaurantId, before, text));
    }

    /** 맛집 행을 잠그고 리뷰 상태가 그대로일 때만 저장한다. */
    private SummaryResponse store(long restaurantId, Fingerprint before, String text) {
        Restaurant locked = restaurants.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Fingerprint after = fingerprint(restaurantId);
        if (after.count() < MIN_REVIEWS) {
            return notEnough((int) after.count());
        }
        if (!after.equals(before)) {
            // 생성 중에 리뷰가 바뀌었다. 이 결과는 예전 리뷰 기준이므로 캐시하지 않는다. 다음 요청에서 다시 만든다.
            return new SummaryResponse(STATUS_READY, text, (int) after.count(), false, LocalDateTime.now(), null);
        }
        Optional<ReviewSummary> existing = summaries.findByRestaurantId(restaurantId);
        if (existing.isPresent()) {
            return ready(existing.get(), true);
        }
        ReviewSummary saved = summaries.saveAndFlush(ReviewSummary.create(locked, text, (int) before.count()));
        return ready(saved, false);
    }

    private Optional<ReviewSummary> freshCache(long restaurantId, int currentCount) {
        return summaries.findByRestaurantId(restaurantId)
                .filter(s -> s.getSourceReviewCount() == currentCount);
    }

    private Fingerprint fingerprint(long restaurantId) {
        return new Fingerprint(reviews.countByRestaurantId(restaurantId),
                reviews.maxIdByRestaurantId(restaurantId));
    }

    private List<ReviewText> recentReviews(long restaurantId) {
        return reviews.findByRestaurantIdOrderByCreatedAtDescIdDesc(restaurantId,
                        PageRequest.of(0, ReviewSummaryPrompt.MAX_REVIEWS))
                .map(r -> new ReviewText(r.getRating(), r.getContent()))
                .getContent();
    }

    /** 응답 본문을 검사한다. 비었거나 지나치게 길면 잘못된 응답이다. */
    static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiResponseInvalidException("요약 본문이 비어 있습니다.");
        }
        String text = raw.trim();
        if (text.length() > MAX_SUMMARY_CHARS) {
            throw new AiResponseInvalidException("요약 본문이 허용 길이를 넘었습니다.");
        }
        return text;
    }

    /** 캐시 무효화. 리뷰 작성·삭제 트랜잭션 안에서 호출한다. 맛집 행 잠금을 가진 상태여야 한다. */
    public void invalidate(long restaurantId) {
        summaries.deleteByRestaurantId(restaurantId);
    }

    private static SummaryResponse ready(ReviewSummary s, boolean cached) {
        return new SummaryResponse(STATUS_READY, s.getSummary(), s.getSourceReviewCount(), cached,
                s.getCreatedAt(), null);
    }

    private static SummaryResponse notEnough(int count) {
        return new SummaryResponse(STATUS_NOT_ENOUGH, null, count, null, null, MESSAGE_NOT_ENOUGH);
    }

    private static SummaryResponse unavailable(int count) {
        return new SummaryResponse(STATUS_UNAVAILABLE, null, count, null, null, MESSAGE_UNAVAILABLE);
    }
}
