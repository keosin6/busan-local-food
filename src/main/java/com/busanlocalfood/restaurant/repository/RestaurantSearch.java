package com.busanlocalfood.restaurant.repository;

import com.busanlocalfood.restaurant.domain.Restaurant;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 맛집 목록 검색 조건 (FUNCTION_SPEC F1-02 목록·검색 흐름).
 * 키워드는 이름 또는 주소에 부분 일치한다. 사용자 입력의 LIKE 와일드카드(%, _, 이스케이프 문자)는 문자로 취급한다.
 */
public final class RestaurantSearch {

    /** LIKE 이스케이프 문자. 백슬래시는 MySQL 문자열 리터럴과 얽히므로 쓰지 않는다. */
    static final char ESCAPE = '!';

    private RestaurantSearch() {
    }

    public static Specification<Restaurant> of(Long categoryId, String keyword) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("category").get("id"), categoryId));
            }
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(builder.or(
                        builder.like(root.get("name"), pattern, ESCAPE),
                        builder.like(root.get("address"), pattern, ESCAPE)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** %, _, 이스케이프 문자 앞에 이스케이프 문자를 붙인다. */
    static String escapeLike(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (char c : raw.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                out.append(ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }
}
