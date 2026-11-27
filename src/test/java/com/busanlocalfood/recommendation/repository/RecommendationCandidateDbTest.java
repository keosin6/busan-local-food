package com.busanlocalfood.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.ChatCompletionClient.ChatMessage;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RestaurantRecommendationService;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
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
 * AI 추천 후보 조회의 MySQL 동작 (SQL 001~008 적용 후). 후보는 최근 등록 순으로 최대 100개이고, 후보가 없으면 AI를 부르지 않는다.
 * AI 클라이언트는 목이다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=recommendation-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import(RestaurantRecommendationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecommendationCandidateDbTest {

    @MockitoBean
    private ChatCompletionClient client;

    @Autowired
    private RestaurantRecommendationService service;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Category category;

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("rec-owner@example.com", "hash", "작성자", "rec-sub-owner"));
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
        members.findAll().stream().filter(m -> m.getEmail().startsWith("rec-")).forEach(members::delete);
    }

    private Long newRestaurant(String name) {
        return restaurants.save(Restaurant.create(owner, category,
                new Restaurant.RestaurantFields(name, "부산 해운대구", null, "설명", null, null))).getId();
    }

    @Test
    void 맛집이_없으면_AI를_부르지_않고_안내한다() {
        RecommendationResponse response = service.recommend("국밥");

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.candidateCount()).isZero();
        verify(client, never()).complete(anyList());
    }

    @Test
    void 후보는_최근_등록_순으로_최대_100개만_골라_AI에_보낸다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 102; i++) {
            ids.add(newRestaurant("후보 " + i));
        }
        when(client.complete(anyList())).thenReturn("[]");

        RecommendationResponse response = service.recommend("아무거나");

        assertThat(response.candidateCount()).isEqualTo(100);
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).complete(captor.capture());
        String user = captor.getValue().get(1).content();
        // 가장 최근 등록한 맛집은 후보에 들어가고, 가장 오래된 두 곳은 빠진다.
        assertThat(user).contains("ID: " + ids.get(ids.size() - 1) + " ");
        assertThat(user).doesNotContain("ID: " + ids.get(0) + " ");
        assertThat(user).doesNotContain("ID: " + ids.get(1) + " ");
    }
}
