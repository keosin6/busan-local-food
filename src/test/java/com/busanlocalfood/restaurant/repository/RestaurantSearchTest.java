package com.busanlocalfood.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 검색어의 LIKE 와일드카드가 문자로 취급되도록 이스케이프하는지 확인한다. DB 없이 실행한다. */
class RestaurantSearchTest {

    @Test
    void 퍼센트와_밑줄은_이스케이프_문자를_붙인다() {
        assertThat(RestaurantSearch.escapeLike("100%")).isEqualTo("100!%");
        assertThat(RestaurantSearch.escapeLike("a_b")).isEqualTo("a!_b");
    }

    @Test
    void 이스케이프_문자_자체도_이스케이프한다() {
        assertThat(RestaurantSearch.escapeLike("a!b")).isEqualTo("a!!b");
    }

    @Test
    void 일반_문자는_그대로_둔다() {
        assertThat(RestaurantSearch.escapeLike("해운대 국밥")).isEqualTo("해운대 국밥");
        assertThat(RestaurantSearch.escapeLike("")).isEmpty();
    }
}
