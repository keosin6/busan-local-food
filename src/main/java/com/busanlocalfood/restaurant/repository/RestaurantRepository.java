package com.busanlocalfood.restaurant.repository;

import com.busanlocalfood.restaurant.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 맛집 저장소. 목록 검색은 Specification으로 만든다 (카테고리, 이름·주소 부분 일치, 정렬과 페이지는 Pageable).
 */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {
}
