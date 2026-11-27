package com.busanlocalfood.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈의 최신 맛집 조회 (FUNCTION_SPEC F1-08, MySQL 8, SQL 001~008 적용 후). 최근 등록 순, 개수 제한, 삭제된 맛집 제외,
 * 맛집 수와 무관하게 쿼리 수가 일정한지(N+1 없음)를 Hibernate 통계로 확인한다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=home-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RestaurantService.class, StorageCleanup.class, ImageUrls.class, ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HomeLatestDbTest {

    @MockitoBean
    private ImageStorage storage;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private List<Category> seeded;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("home-owner@example.com", "hash", "작성자", "home-sub-owner"));
        seeded = categories.findAllByOrderBySortOrderAscIdAsc();
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
        members.findAll().stream().filter(m -> m.getEmail().startsWith("home-")).forEach(members::delete);
    }

    private Long newRestaurant(String name, Category category) {
        return restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산 해운대구", null, "설명", null, null))).getId();
    }

    private Statistics statistics() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }

    @Test
    void 최신_맛집은_최근_등록_순으로_개수만큼만_읽는다() {
        Long first = null;
        Long last = null;
        for (int i = 0; i < 10; i++) {
            Long id = newRestaurant("맛집 " + i, seeded.get(0));
            if (first == null) {
                first = id;
            }
            last = id;
        }

        List<RestaurantSummary> latest = restaurantService.latest(8);

        assertThat(latest).hasSize(8);
        assertThat(latest.get(0).id()).isEqualTo(last);
        assertThat(latest).extracting(RestaurantSummary::id).doesNotContain(first);
    }

    @Test
    void 삭제된_맛집은_최신_목록에_나타나지_않는다() {
        Long kept = newRestaurant("남는 맛집", seeded.get(0));
        Long gone = newRestaurant("지운 맛집", seeded.get(0));
        restaurants.deleteById(gone);

        List<RestaurantSummary> latest = restaurantService.latest(8);

        assertThat(latest).extracting(RestaurantSummary::id).containsExactly(kept);
    }

    @Test
    void 카테고리는_맛집_수와_무관하게_같은_쿼리_수로_읽는다() {
        for (int i = 0; i < 2; i++) {
            newRestaurant("적은 " + i, seeded.get(i));
        }
        Statistics few = statistics();
        restaurantService.latest(8);
        long fewStatements = few.getPrepareStatementCount();

        for (int i = 0; i < 8; i++) {
            newRestaurant("많은 " + i, seeded.get(i % 3));
        }
        Statistics many = statistics();
        List<RestaurantSummary> latest = restaurantService.latest(8);
        long manyStatements = many.getPrepareStatementCount();

        assertThat(latest).hasSize(8);
        // 맛집 2곳일 때와 8곳일 때 쿼리 수가 같다. 맛집마다 카테고리를 따로 읽지 않는다.
        assertThat(manyStatements).isEqualTo(fewStatements);
        assertThat(manyStatements).isLessThanOrEqualTo(3);
    }
}
