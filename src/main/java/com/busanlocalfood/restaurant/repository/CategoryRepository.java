package com.busanlocalfood.restaurant.repository;

import com.busanlocalfood.restaurant.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 카테고리 목록은 화면 표시 순서(sort_order) 기준으로 준다. */
    List<Category> findAllByOrderBySortOrderAscIdAsc();
}
