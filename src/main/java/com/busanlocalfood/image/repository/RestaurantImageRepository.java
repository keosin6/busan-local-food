package com.busanlocalfood.image.repository;

import com.busanlocalfood.image.domain.RestaurantImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantImageRepository extends JpaRepository<RestaurantImage, Long> {

    /** 맛집의 이미지 목록. 대표 이미지는 첫 번째 항목이다. */
    List<RestaurantImage> findByRestaurantIdOrderBySortOrderAscIdAsc(Long restaurantId);

    /** 여러 맛집의 이미지를 한 번에 가져온다. 목록의 대표 이미지 계산에 쓴다. */
    List<RestaurantImage> findByRestaurantIdInOrderBySortOrderAscIdAsc(Collection<Long> restaurantIds);

    /** 맛집에 속한 이미지만 찾는다. 다른 맛집의 이미지 ID로 지우는 일을 막는다. */
    Optional<RestaurantImage> findByIdAndRestaurantId(Long id, Long restaurantId);

    long countByRestaurantId(Long restaurantId);

    /** 현재 가장 큰 sort_order. 이미지가 없으면 -1이다. 새 이미지는 이 값 + 1을 받는다. */
    @Query("select coalesce(max(i.sortOrder), -1) from RestaurantImage i where i.restaurant.id = :restaurantId")
    int maxSortOrder(@Param("restaurantId") Long restaurantId);

    /** 여러 맛집의 이미지 개수를 한 번에 센다. 행은 [맛집 ID, 개수]다. 관리자 목록에 쓴다. */
    @Query("select i.restaurant.id, count(i) from RestaurantImage i where i.restaurant.id in :restaurantIds group by i.restaurant.id")
    List<Object[]> countByRestaurantIds(@Param("restaurantIds") Collection<Long> restaurantIds);
}
