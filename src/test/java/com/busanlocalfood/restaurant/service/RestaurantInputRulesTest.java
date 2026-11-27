package com.busanlocalfood.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 맛집 입력 검증 규칙 (FUNCTION_SPEC F1-02, DATABASE_ERD 2.3). DB 없이 실행한다. */
class RestaurantInputRulesTest {

    private static RestaurantRequest valid() {
        return new RestaurantRequest("  해운대 돼지국밥  ", 1L, " 부산 해운대구 ", "051-000-0000",
                "  국밥 맛집  ", new BigDecimal("35.1587"), new BigDecimal("129.1604"));
    }

    private static void assertInvalid(RestaurantRequest request) {
        assertThatThrownBy(() -> RestaurantInputRules.validate(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static RestaurantRequest with(String name, Long categoryId, String address, String phone,
                                          String description, BigDecimal latitude, BigDecimal longitude) {
        return new RestaurantRequest(name, categoryId, address, phone, description, latitude, longitude);
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    @Test
    void 정상_입력은_공백을_지우고_좌표를_일곱_자리로_맞춘다() {
        RestaurantInputRules.Validated result = RestaurantInputRules.validate(valid());

        assertThat(result.categoryId()).isEqualTo(1L);
        assertThat(result.fields().name()).isEqualTo("해운대 돼지국밥");
        assertThat(result.fields().address()).isEqualTo("부산 해운대구");
        assertThat(result.fields().description()).isEqualTo("국밥 맛집");
        assertThat(result.fields().phone()).isEqualTo("051-000-0000");
        assertThat(result.fields().latitude()).isEqualByComparingTo("35.1587");
        assertThat(result.fields().latitude().scale()).isEqualTo(7);
    }

    @Test
    void 선택_항목이_비어_있으면_null로_저장한다() {
        RestaurantInputRules.Validated result = RestaurantInputRules.validate(
                with("이름", 1L, "주소", "   ", "설명", null, null));

        assertThat(result.fields().phone()).isNull();
        assertThat(result.fields().latitude()).isNull();
        assertThat(result.fields().longitude()).isNull();
    }

    @Test
    void 이름은_1자에서_50자까지만_허용한다() {
        assertInvalid(with("   ", 1L, "주소", null, "설명", null, null));
        assertInvalid(with(repeat('가', 51), 1L, "주소", null, "설명", null, null));
        assertThat(RestaurantInputRules.validate(with(repeat('가', 50), 1L, "주소", null, "설명", null, null))
                .fields().name()).hasSize(50);
    }

    @Test
    void 카테고리는_필수다() {
        assertInvalid(with("이름", null, "주소", null, "설명", null, null));
    }

    @Test
    void 주소는_1자에서_200자까지만_허용한다() {
        assertInvalid(with("이름", 1L, "", null, "설명", null, null));
        assertInvalid(with("이름", 1L, repeat('a', 201), null, "설명", null, null));
        assertThat(RestaurantInputRules.validate(with("이름", 1L, repeat('a', 200), null, "설명", null, null)).fields()
                .address()).hasSize(200);
    }

    @Test
    void 설명은_1자에서_2000자까지만_허용한다() {
        assertInvalid(with("이름", 1L, "주소", null, "", null, null));
        assertInvalid(with("이름", 1L, "주소", null, repeat('a', 2001), null, null));
        assertThat(RestaurantInputRules.validate(with("이름", 1L, "주소", null, repeat('a', 2000), null, null))
                .fields().description()).hasSize(2000);
    }

    @Test
    void 전화번호는_숫자와_하이픈만_허용하고_20자를_넘지_않는다() {
        assertInvalid(with("이름", 1L, "주소", "02 123", "설명", null, null));
        assertInvalid(with("이름", 1L, "주소", "abc", "설명", null, null));
        assertInvalid(with("이름", 1L, "주소", repeat('1', 21), "설명", null, null));
        assertThat(RestaurantInputRules.validate(with("이름", 1L, "주소", "051-123-4567", "설명", null, null))
                .fields().phone()).isEqualTo("051-123-4567");
    }

    @Test
    void 위도는_플러스마이너스_90도_안에서만_받는다() {
        assertInvalid(with("이름", 1L, "주소", null, "설명", new BigDecimal("91"), null));
        assertInvalid(with("이름", 1L, "주소", null, "설명", new BigDecimal("-90.0000001"), null));
        assertThat(RestaurantInputRules.validate(with("이름", 1L, "주소", null, "설명", new BigDecimal("90"), null))
                .fields().latitude()).isEqualByComparingTo("90");
    }

    @Test
    void 경도는_플러스마이너스_180도_안에서만_받는다() {
        assertInvalid(with("이름", 1L, "주소", null, "설명", null, new BigDecimal("181")));
        assertThat(RestaurantInputRules.validate(with("이름", 1L, "주소", null, "설명", null, new BigDecimal("-180")))
                .fields().longitude()).isEqualByComparingTo("-180");
    }

    @Test
    void 소수점_여덟_자리_좌표는_DECIMAL_10_7에_맞지_않아_거부한다() {
        assertInvalid(with("이름", 1L, "주소", null, "설명", new BigDecimal("35.12345678"), null));
    }

    @Test
    void 요청_본문이_없으면_거부한다() {
        assertThatThrownBy(() -> RestaurantInputRules.validate(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 검색어는_공백을_지우고_비면_조건을_없앤다() {
        assertThat(RestaurantInputRules.keyword(null)).isNull();
        assertThat(RestaurantInputRules.keyword("   ")).isNull();
        assertThat(RestaurantInputRules.keyword("  국밥  ")).isEqualTo("국밥");
        assertThatThrownBy(() -> RestaurantInputRules.keyword(repeat('a', 51)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 페이지와_크기는_허용된_범위만_받는다() {
        assertThat(RestaurantInputRules.page(0)).isZero();
        assertThat(RestaurantInputRules.size(1)).isEqualTo(1);
        assertThat(RestaurantInputRules.size(50)).isEqualTo(50);
        assertThatThrownBy(() -> RestaurantInputRules.page(-1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RestaurantInputRules.size(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RestaurantInputRules.size(51)).isInstanceOf(BusinessException.class);
    }
}
