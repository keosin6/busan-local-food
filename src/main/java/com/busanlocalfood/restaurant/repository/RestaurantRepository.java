package com.busanlocalfood.restaurant.repository;

import com.busanlocalfood.restaurant.domain.Restaurant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 맛집 저장소. 목록 검색은 Specification으로 만든다 (카테고리, 이름·주소 부분 일치, 정렬과 페이지는 Pageable).
 */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    /**
     * 맛집 행을 잠그고 조회한다. 이미지 업로드는 이 잠금 안에서 개수를 세고 저장해, 동시 업로드로 5장을 넘지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.id = :id")
    Optional<Restaurant> findByIdForUpdate(@Param("id") long id);

    /**
     * AI 추천 후보 (FUNCTION_SPEC F1-07). 최근 등록 순으로 최대 100개다. 카테고리를 한 번에 읽어 항목마다 추가 조회가 없다.
     */
    @EntityGraph(attributePaths = "category")
    List<Restaurant> findTop100ByOrderByCreatedAtDescIdDesc();

    /**
     * 홈의 최신 맛집 (FUNCTION_SPEC F1-08). 최근 등록 순이고 Pageable로 개수를 제한한다. 카테고리는 같은 쿼리에서 읽는다.
     */
    @EntityGraph(attributePaths = "category")
    List<Restaurant> findByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * 관리자 맛집 목록 (FUNCTION_SPEC F1-09). 일반 목록과 같은 검색 조건을 쓰되, 작성자와 카테고리를 같은 쿼리에서 읽는다.
     */
    @EntityGraph(attributePaths = {"member", "category"})
    @Override
    Page<Restaurant> findAll(Specification<Restaurant> spec, Pageable pageable);

    long countByMemberId(Long memberId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** 여러 회원의 맛집 수를 한 번에 센다. 행은 [회원 ID, 개수]다. */
    @Query("select r.member.id, count(r) from Restaurant r where r.member.id in :memberIds group by r.member.id")
    List<Object[]> countByMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
