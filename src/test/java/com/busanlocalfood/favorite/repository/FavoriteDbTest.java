package com.busanlocalfood.favorite.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.favorite.dto.FavoriteDtos.FavoriteState;
import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜의 MySQL 동작 (SQL 001~008 적용 후). 찜하기와 취소, 중복과 없는 대상, 회원별 분리, 최신 찜 순, 페이지,
 * 맛집 삭제와 회원 삭제 시 정리, UNIQUE 제약, 동시 찜 요청을 확인한다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=favorite-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({FavoriteService.class, RestaurantService.class, StorageCleanup.class, ImageUrls.class,
        ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FavoriteDbTest {

    private static final int ROUNDS = 10;
    private static final long WAIT_SECONDS = 30;

    @MockitoBean
    private ImageStorage storage;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Member alice;
    private Member bob;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("fav-owner@example.com", "hash", "작성자", "fav-sub-owner"));
        alice = members.save(Member.create("fav-alice@example.com", "hash", "앨리스", "fav-sub-alice"));
        bob = members.save(Member.create("fav-bob@example.com", "hash", "밥", "fav-sub-bob"));
        category = categories.findAllByOrderBySortOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM storage_delete_task");
        jdbc.update("DELETE FROM favorite");
        jdbc.update("DELETE FROM review");
        jdbc.update("DELETE FROM restaurant_image");
        restaurants.deleteAll();
        members.findAll().stream().filter(m -> m.getEmail().startsWith("fav-")).forEach(members::delete);
    }

    private Long newRestaurant(String name) {
        return restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산 해운대구", null, "설명", null, null))).getId();
    }

    private long favoriteRows(Long restaurantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE restaurant_id = ?", Long.class,
                restaurantId);
        return count == null ? 0 : count;
    }

    private void setCreatedAt(Long restaurantId, Long memberId, String createdAt) {
        jdbc.update("UPDATE favorite SET created_at = ? WHERE restaurant_id = ? AND member_id = ?",
                createdAt, restaurantId, memberId);
    }

    @Test
    void 정상_찜_등록은_찜_수와_찜_여부를_돌려주고_행을_하나_만든다() {
        Long id = newRestaurant("찜 맛집");

        FavoriteState state = favoriteService.favorite(id, alice.getId());

        assertThat(state.favorited()).isTrue();
        assertThat(state.favoriteCount()).isEqualTo(1);
        assertThat(favoriteRows(id)).isEqualTo(1);
    }

    @Test
    void 같은_회원이_같은_맛집을_다시_찜하면_FAVORITE_ALREADY_EXISTS이고_행은_그대로다() {
        Long id = newRestaurant("중복 맛집");
        favoriteService.favorite(id, alice.getId());

        assertThatThrownBy(() -> favoriteService.favorite(id, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS));
        assertThat(favoriteRows(id)).isEqualTo(1);
    }

    @Test
    void 서로_다른_회원은_같은_맛집을_각자_찜할_수_있다() {
        Long id = newRestaurant("공유 맛집");

        favoriteService.favorite(id, alice.getId());
        FavoriteState bobState = favoriteService.favorite(id, bob.getId());

        assertThat(bobState.favoriteCount()).isEqualTo(2);
        assertThat(favoriteRows(id)).isEqualTo(2);
    }

    @Test
    void 찜_취소는_본인_찜만_지우고_다른_회원의_찜은_그대로_둔다() {
        Long id = newRestaurant("취소 맛집");
        favoriteService.favorite(id, alice.getId());
        favoriteService.favorite(id, bob.getId());

        FavoriteState state = favoriteService.unfavorite(id, alice.getId());

        assertThat(state.favorited()).isFalse();
        assertThat(state.favoriteCount()).isEqualTo(1);
        Long bobRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM favorite WHERE restaurant_id = ? AND member_id = ?", Long.class, id, bob.getId());
        assertThat(bobRows).isEqualTo(1);
    }

    @Test
    void 이미_취소된_찜을_다시_취소하면_FAVORITE_NOT_FOUND다() {
        Long id = newRestaurant("두번 취소");
        favoriteService.favorite(id, alice.getId());
        favoriteService.unfavorite(id, alice.getId());

        assertThatThrownBy(() -> favoriteService.unfavorite(id, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_NOT_FOUND));
    }

    @Test
    void 찜하지_않은_맛집을_취소하면_FAVORITE_NOT_FOUND다() {
        Long id = newRestaurant("찜 안 함");

        assertThatThrownBy(() -> favoriteService.unfavorite(id, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FAVORITE_NOT_FOUND));
    }

    @Test
    void 없는_맛집은_찜하기와_취소_모두_RESTAURANT_NOT_FOUND다() {
        long missing = 987_654_321L;

        assertThatThrownBy(() -> favoriteService.favorite(missing, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
        assertThatThrownBy(() -> favoriteService.unfavorite(missing, alice.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Test
    void 내_찜_목록은_회원별로_분리된다() {
        Long first = newRestaurant("첫째");
        Long second = newRestaurant("둘째");
        favoriteService.favorite(first, alice.getId());
        favoriteService.favorite(second, bob.getId());

        PageResponse<RestaurantSummary> aliceList = favoriteService.list(alice.getId(), 0, 12);

        assertThat(aliceList.totalElements()).isEqualTo(1);
        assertThat(aliceList.content()).extracting(RestaurantSummary::id).containsExactly(first);
    }

    @Test
    void 내_찜_목록은_최신_찜_순으로_정렬된다() {
        Long a = newRestaurant("가장 먼저");
        Long b = newRestaurant("가운데");
        Long c = newRestaurant("가장 나중");
        favoriteService.favorite(a, alice.getId());
        favoriteService.favorite(b, alice.getId());
        favoriteService.favorite(c, alice.getId());
        setCreatedAt(a, alice.getId(), "2026-11-01 10:00:00.000000");
        setCreatedAt(b, alice.getId(), "2026-11-02 10:00:00.000000");
        setCreatedAt(c, alice.getId(), "2026-11-03 10:00:00.000000");

        PageResponse<RestaurantSummary> list = favoriteService.list(alice.getId(), 0, 12);

        assertThat(list.content()).extracting(RestaurantSummary::id).containsExactly(c, b, a);
    }

    @Test
    void 내_찜_목록은_페이지로_나뉘고_총_개수와_페이지_수를_준다() {
        for (int i = 0; i < 3; i++) {
            favoriteService.favorite(newRestaurant("페이지 " + i), alice.getId());
        }

        PageResponse<RestaurantSummary> first = favoriteService.list(alice.getId(), 0, 2);
        PageResponse<RestaurantSummary> second = favoriteService.list(alice.getId(), 1, 2);

        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).doesNotContainAnyElementsOf(second.content());
    }

    @Test
    void 목록의_페이지_크기가_범위를_벗어나면_VALIDATION_FAILED다() {
        assertThatThrownBy(() -> favoriteService.list(alice.getId(), 0, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> favoriteService.list(alice.getId(), 0, 51))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 맛집을_삭제하면_그_맛집의_찜이_함께_지워지고_목록에_나타나지_않는다() {
        Long kept = newRestaurant("남는 맛집");
        Long gone = newRestaurant("지울 맛집");
        favoriteService.favorite(kept, alice.getId());
        favoriteService.favorite(gone, alice.getId());
        favoriteService.favorite(gone, bob.getId());

        restaurantService.delete(gone, owner.getId());

        assertThat(favoriteRows(gone)).isZero();
        PageResponse<RestaurantSummary> list = favoriteService.list(alice.getId(), 0, 12);
        assertThat(list.content()).extracting(RestaurantSummary::id).containsExactly(kept);
    }

    @Test
    void 맛집_상세는_찜_수와_현재_회원의_찜_여부를_돌려준다() {
        Long id = newRestaurant("상세 찜");
        favoriteService.favorite(id, alice.getId());
        favoriteService.favorite(id, bob.getId());

        RestaurantDetail forAlice = restaurantService.detail(id, alice.getId());
        RestaurantDetail forGuest = restaurantService.detail(id, null);

        assertThat(forAlice.favoriteCount()).isEqualTo(2);
        assertThat(forAlice.isFavorited()).isTrue();
        assertThat(forGuest.favoriteCount()).isEqualTo(2);
        assertThat(forGuest.isFavorited()).isFalse();
    }

    @Test
    void 같은_회원과_맛집의_찜_행은_DB_UNIQUE_제약이_한_번만_허용한다() {
        Long id = newRestaurant("유니크");
        favoriteService.favorite(id, alice.getId());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)", alice.getId(), id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_favorite_member_restaurant");
        assertThat(favoriteRows(id)).isEqualTo(1);
    }

    @Test
    void 회원을_지우면_그_회원의_찜이_DB_CASCADE로_함께_지워진다() {
        Long id = newRestaurant("회원 삭제");
        favoriteService.favorite(id, alice.getId());
        favoriteService.favorite(id, bob.getId());

        jdbc.update("DELETE FROM member WHERE id = ?", alice.getId());

        Long aliceRows = jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE member_id = ?", Long.class,
                alice.getId());
        assertThat(aliceRows).isZero();
        assertThat(favoriteRows(id)).isEqualTo(1);
    }

    @Test
    void 같은_회원의_동시_찜_요청은_한_번만_성공하고_행은_하나다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long id = newRestaurant("동시 찜 " + round);
            Long memberId = alice.getId();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier start = new CyclicBarrier(2);
            try {
                List<Future<String>> outcomes = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    outcomes.add(pool.submit(() -> {
                        start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                        try {
                            favoriteService.favorite(id, memberId);
                            return "ok";
                        } catch (BusinessException e) {
                            return e.getErrorCode().name();
                        }
                    }));
                }
                List<String> results = new ArrayList<>();
                for (Future<String> f : outcomes) {
                    results.add(f.get(WAIT_SECONDS, TimeUnit.SECONDS));
                }
                assertThat(results).containsExactlyInAnyOrder("ok", "FAVORITE_ALREADY_EXISTS");
                assertThat(favoriteRows(id)).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
