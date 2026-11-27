package com.busanlocalfood.review.repository;

import com.busanlocalfood.review.domain.Review;
import java.time.LocalDateTime;
import java.util.Collection;
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

    long countByMemberId(Long memberId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** 여러 회원의 리뷰 수를 한 번에 센다. 행은 [회원 ID, 개수]다. */
    @Query("select r.member.id, count(r) from Review r where r.member.id in :memberIds group by r.member.id")
    List<Object[]> countByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    /**
     * 관리자 리뷰 목록 (FUNCTION_SPEC F1-09). 맛집 ID와 회원 ID는 선택 필터다. 최신순이다.
     * 작성자와 맛집을 같은 쿼리에서 읽어 항목마다 추가 조회가 없다.
     */
    @Query(value = "select r from Review r join fetch r.member m join fetch r.restaurant rs "
            + "where (:restaurantId is null or rs.id = :restaurantId) and (:memberId is null or m.id = :memberId) "
            + "order by r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Review r "
            + "where (:restaurantId is null or r.restaurant.id = :restaurantId) "
            + "and (:memberId is null or r.member.id = :memberId)")
    Page<Review> findAdminPage(@Param("restaurantId") Long restaurantId, @Param("memberId") Long memberId,
                               Pageable pageable);
}
