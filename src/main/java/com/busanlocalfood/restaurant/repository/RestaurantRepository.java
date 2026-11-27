package com.busanlocalfood.restaurant.repository;

import com.busanlocalfood.restaurant.domain.Restaurant;
import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
