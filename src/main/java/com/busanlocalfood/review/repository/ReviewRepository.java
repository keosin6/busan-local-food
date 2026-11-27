package com.busanlocalfood.review.repository;

import com.busanlocalfood.review.domain.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByRestaurantIdAndMemberId(Long restaurantId, Long memberId);

    long countByRestaurantId(Long restaurantId);

    /** 맛집 리뷰 평점의 합. 평균은 이 값과 개수로 정확히 계산한다. 리뷰가 없으면 0이다. */
    @Query("select coalesce(sum(r.rating), 0) from Review r where r.restaurant.id = :restaurantId")
    long sumRatingByRestaurantId(@Param("restaurantId") Long restaurantId);

    /** 맛집 리뷰 중 가장 큰 ID. 리뷰가 없으면 0이다. 새 리뷰는 항상 더 큰 ID를 받으므로 요약 생성 전후 비교에 쓴다. */
    @Query("select coalesce(max(r.id), 0) from Review r where r.restaurant.id = :restaurantId")
    long maxIdByRestaurantId(@Param("restaurantId") Long restaurantId);

    /** 리뷰가 속한 맛집 ID만 읽는다. 삭제 요청의 잠금 대상을 찾는 데 쓴다. */
    @Query("select r.restaurant.id from Review r where r.id = :id")
    Optional<Long> findRestaurantIdById(@Param("id") Long id);

    /** 최신순 목록. 작성자 닉네임을 같이 읽어 목록 조회 중 추가 조회가 생기지 않게 한다. */
    @EntityGraph(attributePaths = "member")
    Page<Review> findByRestaurantIdOrderByCreatedAtDescIdDesc(Long restaurantId, Pageable pageable);

    List<Review> findByRestaurantId(Long restaurantId);
}
