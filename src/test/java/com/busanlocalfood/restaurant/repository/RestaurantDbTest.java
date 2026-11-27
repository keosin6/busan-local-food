package com.busanlocalfood.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 맛집의 MySQL 동작 (SQL 001~004 적용 후). 카테고리 시드, 등록 기본값, 작성자 수정·삭제,
 * 검색 이스케이프, 페이지, 평점 정렬, RESTRICT 외래키를 확인한다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 SQL 001 → 002 → 003 → 004를 순서대로 적용해야 한다. 맛집과 회원 행은 테스트 전후로 지운다.
 * 이 테스트 클래스는 테스트 DB 전용이다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import(RestaurantService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RestaurantDbTest {

    private static final List<String> SEED_NAMES = List.of("한식", "중식", "일식", "양식", "분식",
            "카페·디저트", "해산물", "고기·구이", "술집", "기타");

    @Autowired
    private RestaurantService service;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Member other;
    private Long koreanId;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("db-owner@example.com", "hash", "작성자", "db-sub-owner"));
        other = members.save(Member.create("db-other@example.com", "hash", "다른회원", "db-sub-other"));
        koreanId = categories.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        restaurants.deleteAll();
        members.findAll().stream()
                .filter(m -> m.getEmail().startsWith("db-"))
                .forEach(members::delete);
    }

    private static RestaurantRequest request(String name, Long categoryId) {
        return new RestaurantRequest(name, categoryId, "부산 해운대구", null, "설명", null, null);
    }

    @Test
    void 카테고리_시드는_10개이고_sort_order_순서다() {
        List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();

        assertThat(all).extracting(Category::getName).containsExactlyElementsOf(SEED_NAMES);
        assertThat(all).extracting(Category::getSortOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void 등록하면_평점은_0점0_리뷰는_0개로_시작하고_작성자가_보인다() {
        RestaurantDetail created = service.create(owner.getId(), request("해운대 국밥", koreanId));

        assertThat(created.averageRating()).isEqualByComparingTo("0.0");
        assertThat(created.reviewCount()).isZero();
        assertThat(created.isOwner()).isTrue();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(created.owner().memberId()).isEqualTo(owner.getId());
    }

    @Test
    void 없는_카테고리로_등록하면_CATEGORY_NOT_FOUND이고_저장되지_않는다() {
        assertThatThrownBy(() -> service.create(owner.getId(), request("이름", 999_999L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
        assertThat(restaurants.count()).isZero();
    }

    @Test
    void 다른_회원은_수정과_삭제를_못하고_작성자는_수정하면_새_값이_남는다() {
        RestaurantDetail created = service.create(owner.getId(), request("원래 이름", koreanId));
        long id = created.id();

        assertThatThrownBy(() -> service.update(id, other.getId(), request("바뀜", koreanId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.delete(id, other.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(restaurants.findById(id).orElseThrow().getName()).isEqualTo("원래 이름");

        service.update(id, owner.getId(), request("새 이름", koreanId));
        assertThat(restaurants.findById(id).orElseThrow().getName()).isEqualTo("새 이름");
    }

    @Test
    void 작성자가_삭제하면_행이_완전히_지워진다() {
        long id = service.create(owner.getId(), request("지울 곳", koreanId)).id();

        assertThat(service.delete(id, owner.getId()).deletedImageCount()).isZero();
        assertThat(restaurants.findById(id)).isEmpty();
        assertThatThrownBy(() -> service.detail(id, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    void 키워드의_퍼센트와_밑줄은_문자로_검색되고_와일드카드가_아니다() {
        service.create(owner.getId(), request("A%B", koreanId));
        service.create(owner.getId(), request("AXB", koreanId));
        service.create(owner.getId(), request("a_b", koreanId));
        service.create(owner.getId(), request("axb", koreanId));

        assertThat(names(service.list(null, "%", "latest", 0, 12))).containsExactly("A%B");
        assertThat(names(service.list(null, "_", "latest", 0, 12))).containsExactly("a_b");
        assertThat(names(service.list(null, "AXB", "latest", 0, 12))).containsExactlyInAnyOrder("AXB", "axb");
    }

    @Test
    void 카테고리_필터는_해당_카테고리만_돌려준다() {
        long japanese = categories.findAllByOrderBySortOrderAscIdAsc().get(2).getId();
        service.create(owner.getId(), request("한식집", koreanId));
        service.create(owner.getId(), request("일식집", japanese));

        assertThat(names(service.list(japanese, null, "latest", 0, 12))).containsExactly("일식집");
    }

    @Test
    void 페이지는_전체_개수와_마지막_페이지를_맞게_계산한다() {
        for (int i = 0; i < 3; i++) {
            service.create(owner.getId(), request("맛집" + i, koreanId));
        }

        PageResponse<RestaurantSummary> first = service.list(null, null, "latest", 0, 2);
        PageResponse<RestaurantSummary> second = service.list(null, null, "latest", 1, 2);

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(1);
    }

    @Test
    void 평점순_정렬은_평점이_높은_곳이_먼저_온다() {
        long low = service.create(owner.getId(), request("낮은 평점", koreanId)).id();
        long high = service.create(owner.getId(), request("높은 평점", koreanId)).id();
        jdbc.update("UPDATE restaurant SET average_rating = 4.5 WHERE id = ?", high);
        jdbc.update("UPDATE restaurant SET average_rating = 2.0 WHERE id = ?", low);

        assertThat(names(service.list(null, null, "rating", 0, 12))).containsExactly("높은 평점", "낮은 평점");
    }

    @Test
    void 카테고리에_맛집이_있으면_카테고리_삭제는_막힌다() {
        service.create(owner.getId(), request("묶인 맛집", koreanId));

        assertThatThrownBy(() -> categories.deleteById(koreanId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(categories.findById(koreanId)).isPresent();
    }

    private static List<String> names(PageResponse<RestaurantSummary> page) {
        return page.content().stream().map(RestaurantSummary::name).toList();
    }
}
