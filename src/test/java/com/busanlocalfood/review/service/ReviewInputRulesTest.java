package com.busanlocalfood.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 리뷰 입력 규칙과 평균 반올림 (FUNCTION_SPEC F1-04). DB 없이 실행한다. */
class ReviewInputRulesTest {

    private static ReviewRequest request(String rating, String content) {
        return new ReviewRequest(rating == null ? null : new BigDecimal(rating), content);
    }

    private static void assertInvalid(ReviewRequest r) {
        assertThatThrownBy(() -> ReviewInputRules.validate(r))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 평점_1점과_5점은_허용하고_정수로_바꾼다() {
        assertThat(ReviewInputRules.validate(request("1", "좋아요")).rating()).isEqualTo(1);
        assertThat(ReviewInputRules.validate(request("5", "좋아요")).rating()).isEqualTo(5);
        assertThat(ReviewInputRules.validate(request("5.0", "좋아요")).rating()).isEqualTo(5);
    }

    @Test
    void 평점_0점과_6점은_거부한다() {
        assertInvalid(request("0", "내용"));
        assertInvalid(request("6", "내용"));
        assertInvalid(request("-1", "내용"));
    }

    @Test
    void 소수_평점은_정수가_아니므로_거부한다() {
        assertInvalid(request("5.5", "내용"));
        assertInvalid(request("3.01", "내용"));
    }

    @Test
    void 평점이_없으면_거부한다() {
        assertInvalid(request(null, "내용"));
        assertInvalid(null);
    }

    @Test
    void 내용은_공백을_뺀_뒤_1자에서_500자까지만_허용한다() {
        assertInvalid(request("3", "   "));
        assertInvalid(request("3", null));
        assertInvalid(request("3", "a".repeat(501)));
        assertThat(ReviewInputRules.validate(request("3", "  " + "a".repeat(500) + "  ")).content()).hasSize(500);
        assertThat(ReviewInputRules.validate(request("3", "  좋아요  ")).content()).isEqualTo("좋아요");
    }

    @Test
    void 목록_페이지와_크기는_범위만_받는다() {
        assertThat(ReviewInputRules.page(0)).isZero();
        assertThat(ReviewInputRules.size(10)).isEqualTo(10);
        assertThatThrownBy(() -> ReviewInputRules.page(-1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReviewInputRules.size(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReviewInputRules.size(51)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 평균은_합계와_개수로_계산하고_소수_첫째_자리에서_반올림한다() {
        assertThat(ReviewService.averageOf(0, 0)).isEqualByComparingTo("0.0");
        assertThat(ReviewService.averageOf(5, 1)).isEqualByComparingTo("5.0");
        assertThat(ReviewService.averageOf(9, 2)).isEqualByComparingTo("4.5");
        assertThat(ReviewService.averageOf(4, 3)).isEqualByComparingTo("1.3");
        assertThat(ReviewService.averageOf(7, 4)).isEqualByComparingTo("1.8");
        assertThat(ReviewService.averageOf(13, 3)).isEqualByComparingTo("4.3");
    }
}
