package com.busanlocalfood.review.repository;

import com.busanlocalfood.review.domain.ReviewSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, Long> {

    Optional<ReviewSummary> findByRestaurantId(Long restaurantId);

    /** 캐시 무효화. 리뷰 작성·삭제 트랜잭션 안에서 호출한다. 행이 없어도 실패하지 않는다. */
    long deleteByRestaurantId(Long restaurantId);
}
