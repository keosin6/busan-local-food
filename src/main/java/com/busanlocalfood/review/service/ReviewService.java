package com.busanlocalfood.review.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.security.AdminAccess;
import org.springframework.security.core.Authentication;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.review.domain.Review;
import com.busanlocalfood.review.dto.ReviewDtos.AuthorRef;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewCreated;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewDeleteResult;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewItem;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.repository.ReviewRepository;
import com.busanlocalfood.review.repository.ReviewSummaryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리뷰 서비스 (FUNCTION_SPEC F1-04, API_SPEC 6.1~6.3).
 *
 * 동시성: 리뷰 작성과 삭제는 맛집 행을 먼저 잠근다(PESSIMISTIC_WRITE). 맛집 삭제, 이미지 업로드와 같은 행을 쓰므로
 * 같은 맛집의 쓰기는 한 번에 하나씩 진행된다. 잠금 뒤에 리뷰 행에서 개수와 평점 합을 다시 세서 맛집 값을 맞춘다.
 * 한 회원의 중복 작성은 잠금 안의 존재 확인과 uk_review_member_restaurant 제약으로 막는다.
 *
 * 권한: 리뷰 삭제는 작성자 본인만 한다. 관리자 삭제는 F1-09의 별도 API다. 여기에는 관리자 우회가 없다.
 * 캐시: AI 요약 캐시(F1-05)는 아직 없다. 캐시가 생기면 작성·삭제 트랜잭션에서 같이 무효화한다.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final RestaurantRepository restaurants;
    private final MemberRepository members;
    private final ReviewSummaryRepository summaries;
    private final TransactionTemplate tx;

    public ReviewService(ReviewRepository reviews, RestaurantRepository restaurants, MemberRepository members,
                         ReviewSummaryRepository summaries, PlatformTransactionManager transactionManager) {
        this.reviews = reviews;
        this.restaurants = restaurants;
        this.members = members;
        this.summaries = summaries;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 리뷰 목록 (API_SPEC 6.1). 비회원도 조회한다. 최신순이다. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewItem> list(long restaurantId, Long viewerId, int page, int size) {
        int safePage = ReviewInputRules.page(page);
        int safeSize = ReviewInputRules.size(size);
        if (!restaurants.existsById(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        Page<Review> result = reviews.findByRestaurantIdOrderByCreatedAtDescIdDesc(restaurantId,
                PageRequest.of(safePage, safeSize));
        return new PageResponse<>(result.map(r -> toItem(r, viewerId)).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** 리뷰 작성 (API_SPEC 6.2). 맛집 평균과 리뷰 수를 같은 트랜잭션에서 맞춘다. */
    public ReviewCreated create(long restaurantId, long memberId, ReviewRequest request) {
        ReviewInputRules.Validated input = ReviewInputRules.validate(request);
        return tx.execute(status -> {
            Restaurant restaurant = restaurants.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
            Member member = members.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
            if (reviews.existsByRestaurantIdAndMemberId(restaurantId, memberId)) {
                throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
            }
            Review saved;
            try {
                saved = reviews.saveAndFlush(Review.create(restaurant, member, input.rating(), input.content()));
            } catch (DataIntegrityViolationException e) {
                // 잠금 밖에서 같은 제약이 먼저 걸린 경우의 안전장치다. 리뷰와 맛집 값은 함께 롤백된다.
                throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
            }
            BigDecimal average = refreshStatistics(restaurant);
            // AI 요약 캐시 무효화 (F1-05). 같은 트랜잭션, 같은 맛집 행 잠금 안에서 지운다.
            summaries.deleteByRestaurantId(restaurantId);
            return new ReviewCreated(saved.getId(), saved.getRating(), saved.getContent(), average,
                    restaurant.getReviewCount());
        });
    }

    /**
     * 리뷰 삭제 (API_SPEC 6.3). 작성자 본인만 한다.
     * 리뷰가 속한 맛집을 먼저 알아내고, 그 맛집 행을 잠근 뒤 리뷰를 다시 읽는다. 잠금 전에 읽은 값은 쓰지 않는다.
     */
    public DeletedReview delete(long reviewId, long memberId) {
        return deleteReview(reviewId, memberId);
    }

    /** 관리자 리뷰 삭제 (FUNCTION_SPEC F1-09). 작성자 확인만 생략하고, 잠금·재조회·집계 갱신·캐시 삭제는 같다. */
    public DeletedReview deleteAsAdmin(long reviewId, Authentication actor) {
        AdminAccess.requireAdmin(actor);
        return deleteReview(reviewId, null);
    }

    private DeletedReview deleteReview(long reviewId, Long ownerOrNull) {
        Long restaurantId = reviews.findRestaurantIdById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        return tx.execute(status -> {
            Restaurant restaurant = restaurants.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
            Review review = reviews.findById(reviewId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
            if (ownerOrNull != null && !review.isWrittenBy(ownerOrNull)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            reviews.delete(review);
            reviews.flush();
            BigDecimal average = refreshStatistics(restaurant);
            summaries.deleteByRestaurantId(restaurantId);
            return new DeletedReview(restaurantId,
                    new ReviewDeleteResult(average, restaurant.getReviewCount()));
        });
    }

    /** 삭제 결과와 맛집 ID. 화면은 맛집 ID로 돌아가고, API는 ReviewDeleteResult만 응답한다. */
    public record DeletedReview(long restaurantId, ReviewDeleteResult result) {
    }

    /**
     * 맛집의 리뷰 수와 평균을 리뷰 행에서 다시 계산해 맛집에 쓴다. 평균은 합계와 개수의 정확한 나눗셈을
     * 소수 첫째 자리로 반올림한다(HALF_UP). 리뷰가 없으면 0.0이다. 호출자는 맛집 행 잠금을 가진 상태여야 한다.
     */
    private BigDecimal refreshStatistics(Restaurant restaurant) {
        long count = reviews.countByRestaurantId(restaurant.getId());
        long sum = reviews.sumRatingByRestaurantId(restaurant.getId());
        BigDecimal average = averageOf(sum, count);
        restaurant.updateReviewStatistics((int) count, average);
        return average;
    }

    static BigDecimal averageOf(long sum, long count) {
        if (count == 0) {
            return new BigDecimal("0.0");
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private static ReviewItem toItem(Review review, Long viewerId) {
        Member author = review.getMember();
        return new ReviewItem(review.getId(), review.getRating(), review.getContent(),
                new AuthorRef(author.getId(), author.getNickname()),
                viewerId != null && review.isWrittenBy(viewerId),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
