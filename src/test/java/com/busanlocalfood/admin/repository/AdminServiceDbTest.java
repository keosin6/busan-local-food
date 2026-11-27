package com.busanlocalfood.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.busanlocalfood.admin.dto.AdminDtos.AdminMemberItem;
import com.busanlocalfood.admin.dto.AdminDtos.AdminRestaurantItem;
import com.busanlocalfood.admin.dto.AdminDtos.AdminReviewItem;
import com.busanlocalfood.admin.dto.AdminDtos.Dashboard;
import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.admin.dto.AdminDtos.StorageDeletionItem;
import com.busanlocalfood.admin.service.AdminQueryService;
import com.busanlocalfood.admin.service.AdminStorageRetryService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageStorage.StorageException;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.dto.ReviewDtos.ReviewRequest;
import com.busanlocalfood.review.repository.ReviewRepository;
import com.busanlocalfood.review.service.ReviewService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기능의 MySQL 동작 (F1-09, SQL 001~008 적용 후). 목록·검색·정렬·페이지, 맛집 수정과 삭제(작성자 유지, 정리, S3 추적),
 * 리뷰 삭제 집계와 요약 캐시, 회원 집계와 민감 정보 없음, 대시보드 7일 경계(서비스 시간대), S3 재시도 행 처리,
 * 관리자 이미지 작업, 일반 회원의 작성자 검사 유지, 쿼리 수 일정. 스토리지는 목이다. 환경 변수 TEST_DB_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=admin-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RestaurantService.class, ReviewService.class, RestaurantImageService.class, StorageCleanup.class,
        ImageUrls.class, ImageStorageConfig.class, AdminQueryService.class, AdminStorageRetryService.class,
        AdminServiceDbTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminServiceDbTest {

    /** 서비스 시간대의 2026-11-27 10:00. 최근 7일 경계 계산에 쓴다. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock serviceClock() {
            return Clock.fixed(Instant.parse("2026-11-27T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @MockitoBean
    private ImageStorage storage;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private RestaurantImageService imageService;

    @Autowired
    private AdminQueryService queries;

    @Autowired
    private AdminStorageRetryService retry;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private Member admin;
    private Member owner;
    private Member other;
    private Category korean;
    private Category chinese;

    @BeforeEach
    void setUp() {
        cleanRows();
        admin = members.save(Member.create("adm-admin@example.com", "hash", "관리자", "adm-sub-admin"));
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", admin.getId());
        owner = members.save(Member.create("adm-owner@example.com", "hash", "작성자", "adm-sub-owner"));
        other = members.save(Member.create("adm-other@example.com", "hash", "다른회원", "adm-sub-other"));
        List<Category> seeded = categories.findAllByOrderBySortOrderAscIdAsc();
        korean = seeded.get(0);
        chinese = seeded.get(1);
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM storage_delete_task");
        jdbc.update("DELETE FROM ai_review_summary");
        jdbc.update("DELETE FROM favorite");
        jdbc.update("DELETE FROM review");
        jdbc.update("DELETE FROM restaurant_image");
        restaurants.deleteAll();
        members.findAll().stream().filter(m -> m.getEmail().startsWith("adm-")).forEach(members::delete);
    }

    private static Authentication actor(long memberId, String role) {
        return new UsernamePasswordAuthenticationToken(memberId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Authentication admin() {
        return actor(admin.getId(), "ADMIN");
    }

    private Restaurant newRestaurant(String name, String address, Member author, Category category) {
        return restaurants.save(Restaurant.create(author, category,
                new Restaurant.RestaurantFields(name, address, null, "설명", null, null)));
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private long countRows(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private Statistics statistics() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }

    // 맛집 목록

    @Test
    void 관리자_맛집_목록은_검색_카테고리_정렬_페이지가_동작한다() {
        newRestaurant("해운대 국밥", "부산 해운대구", owner, korean);
        newRestaurant("광안리 짜장", "부산 수영구", other, chinese);
        newRestaurant("서면 국수", "부산 부산진구", other, korean);

        PageResponse<AdminRestaurantItem> byKeyword = queries.restaurants(admin(), "국", null, "latest", 0, 20);
        PageResponse<AdminRestaurantItem> byCategory = queries.restaurants(admin(), null, korean.getId(), "latest",
                0, 20);
        PageResponse<AdminRestaurantItem> paged = queries.restaurants(admin(), null, null, "latest", 1, 2);

        assertThat(byKeyword.content()).extracting(AdminRestaurantItem::name)
                .containsExactlyInAnyOrder("해운대 국밥", "서면 국수");
        assertThat(byCategory.content()).hasSize(2);
        assertThat(paged.totalElements()).isEqualTo(3);
        assertThat(paged.content()).hasSize(1);
        assertThat(paged.totalPages()).isEqualTo(2);
    }

    @Test
    void 관리자_맛집_목록은_작성자와_이미지_수를_정확히_준다() {
        Restaurant mine = newRestaurant("작성자 맛집", "부산 해운대구", owner, korean);
        jdbc.update("INSERT INTO restaurant_image (restaurant_id, s3_key, original_filename, content_type, file_size, sort_order) "
                + "VALUES (?, 'k1', 'a.jpg', 'image/jpeg', 10, 0), (?, 'k2', 'b.jpg', 'image/jpeg', 10, 1)",
                mine.getId(), mine.getId());

        AdminRestaurantItem item = queries.restaurants(admin(), null, null, "latest", 0, 20).content().get(0);

        assertThat(item.owner().email()).isEqualTo("adm-owner@example.com");
        assertThat(item.owner().memberId()).isEqualTo(owner.getId());
        assertThat(item.imageCount()).isEqualTo(2);
    }

    @Test
    void 관리자_맛집_목록은_맛집_수와_무관하게_쿼리_수가_같다() {
        for (int i = 0; i < 2; i++) {
            newRestaurant("적은 " + i, "부산 " + i, owner, korean);
        }
        Statistics few = statistics();
        queries.restaurants(admin(), null, null, "latest", 0, 20);
        long fewStatements = few.getPrepareStatementCount();

        for (int i = 0; i < 8; i++) {
            newRestaurant("많은 " + i, "부산 " + i, i % 2 == 0 ? owner : other, i % 3 == 0 ? chinese : korean);
        }
        Statistics many = statistics();
        PageResponse<AdminRestaurantItem> page = queries.restaurants(admin(), null, null, "latest", 0, 20);
        long manyStatements = many.getPrepareStatementCount();

        assertThat(page.content()).hasSize(10);
        assertThat(manyStatements).isEqualTo(fewStatements);
        assertThat(manyStatements).isLessThanOrEqualTo(4);
    }

    // 맛집 수정과 삭제

    @Test
    void 관리자_수정은_작성자를_바꾸지_않고_내용만_바꾼다() {
        Restaurant target = newRestaurant("원래 이름", "부산 해운대구", owner, korean);

        restaurantService.updateAsAdmin(target.getId(), admin(), new RestaurantRequest("새 이름", chinese.getId(),
                "부산 수영구", null, "새 소개", null, null));

        Restaurant reloaded = restaurants.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("새 이름");
        assertThat(reloaded.getMember().getId()).isEqualTo(owner.getId());
    }

    @Test
    void 관리자는_타인_맛집을_삭제하고_찜_리뷰_요약과_사진_기록이_정리된다() {
        Restaurant target = newRestaurant("삭제 대상", "부산 해운대구", owner, korean);
        jdbc.update("INSERT INTO restaurant_image (restaurant_id, s3_key, original_filename, content_type, file_size, sort_order) "
                + "VALUES (?, 'img-key', 'a.jpg', 'image/jpeg', 10, 0)", target.getId());
        jdbc.update("INSERT INTO favorite (member_id, restaurant_id) VALUES (?, ?)", other.getId(), target.getId());
        reviewService.create(target.getId(), other.getId(), new ReviewRequest(new BigDecimal("4"), "좋아요"));
        jdbc.update("INSERT INTO ai_review_summary (restaurant_id, summary, source_review_count) VALUES (?, '요약', 3)",
                target.getId());

        DeleteResult result = restaurantService.deleteAsAdmin(target.getId(), admin());

        assertThat(result.deletedImageCount()).isEqualTo(1);
        assertThat(result.storageDeleteFailedCount()).isZero();
        assertThat(restaurants.existsById(target.getId())).isFalse();
        assertThat(countRows("SELECT COUNT(*) FROM favorite WHERE restaurant_id = ?", target.getId())).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM review WHERE restaurant_id = ?", target.getId())).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM ai_review_summary WHERE restaurant_id = ?", target.getId())).isZero();
        verify(storage).delete("img-key");
    }

    @Test
    void 관리자_맛집_삭제에서_S3_실패는_맛집_삭제를_되돌리지_않고_추적_행을_남긴다() {
        Restaurant target = newRestaurant("실패 대상", "부산 해운대구", owner, korean);
        jdbc.update("INSERT INTO restaurant_image (restaurant_id, s3_key, original_filename, content_type, file_size, sort_order) "
                + "VALUES (?, 'stuck-key', 'a.jpg', 'image/jpeg', 10, 0)", target.getId());
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 500).")).when(storage).delete("stuck-key");

        DeleteResult result = restaurantService.deleteAsAdmin(target.getId(), admin());

        assertThat(result.storageDeleteFailedCount()).isEqualTo(1);
        assertThat(restaurants.existsById(target.getId())).isFalse();
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task WHERE s3_key = 'stuck-key'")).isEqualTo(1);
    }

    @Test
    void 일반_회원은_타인_맛집을_수정하거나_삭제할_수_없고_행은_그대로다() {
        Restaurant target = newRestaurant("남의 맛집", "부산 해운대구", owner, korean);

        assertCode(() -> restaurantService.delete(target.getId(), other.getId()), ErrorCode.FORBIDDEN);
        assertCode(() -> restaurantService.update(target.getId(), other.getId(),
                new RestaurantRequest("바꿈", korean.getId(), "주소", null, "설명", null, null)), ErrorCode.FORBIDDEN);
        assertThat(restaurants.findById(target.getId()).orElseThrow().getName()).isEqualTo("남의 맛집");
    }

    @Test
    void 회원_actor는_관리자_서비스를_쓸_수_없다() {
        Restaurant target = newRestaurant("보호 대상", "부산 해운대구", owner, korean);

        assertCode(() -> restaurantService.deleteAsAdmin(target.getId(), actor(owner.getId(), "USER")),
                ErrorCode.FORBIDDEN);
        assertThat(restaurants.existsById(target.getId())).isTrue();
    }

    // 리뷰

    @Test
    void 관리자_리뷰_삭제는_평점과_리뷰_수를_다시_계산하고_요약_캐시를_지운다() {
        Restaurant target = newRestaurant("리뷰 대상", "부산 해운대구", owner, korean);
        reviewService.create(target.getId(), owner.getId(), new ReviewRequest(new BigDecimal("5"), "좋아요"));
        reviewService.create(target.getId(), other.getId(), new ReviewRequest(new BigDecimal("3"), "보통"));
        Long third = reviewService.create(target.getId(), members.save(Member.create("adm-third@example.com", "h",
                "셋째", "adm-sub-third")).getId(), new ReviewRequest(new BigDecimal("1"), "별로")).id();
        jdbc.update("INSERT INTO ai_review_summary (restaurant_id, summary, source_review_count) VALUES (?, '요약', 3)",
                target.getId());

        var deleted = reviewService.deleteAsAdmin(third, admin()).result();

        assertThat(deleted.restaurantReviewCount()).isEqualTo(2);
        assertThat(deleted.restaurantAverageRating()).isEqualByComparingTo("4.0");
        Restaurant reloaded = restaurants.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getReviewCount()).isEqualTo(2);
        assertThat(reloaded.getAverageRating()).isEqualByComparingTo("4.0");
        assertThat(countRows("SELECT COUNT(*) FROM ai_review_summary WHERE restaurant_id = ?", target.getId())).isZero();
    }

    @Test
    void 일반_회원은_타인_리뷰를_삭제할_수_없다() {
        Restaurant target = newRestaurant("리뷰 권한", "부산 해운대구", owner, korean);
        Long review = reviewService.create(target.getId(), owner.getId(), new ReviewRequest(new BigDecimal("5"),
                "좋아요")).id();

        assertCode(() -> reviewService.delete(review, other.getId()), ErrorCode.FORBIDDEN);
        assertThat(reviews.existsById(review)).isTrue();
    }

    @Test
    void 관리자_리뷰_목록은_맛집과_회원_필터를_적용하고_최신순이다() {
        Restaurant first = newRestaurant("필터 A", "부산 A", owner, korean);
        Restaurant second = newRestaurant("필터 B", "부산 B", owner, korean);
        reviewService.create(first.getId(), owner.getId(), new ReviewRequest(new BigDecimal("5"), "A1"));
        reviewService.create(first.getId(), other.getId(), new ReviewRequest(new BigDecimal("4"), "A2"));
        reviewService.create(second.getId(), other.getId(), new ReviewRequest(new BigDecimal("3"), "B1"));

        PageResponse<AdminReviewItem> all = queries.reviews(admin(), null, null, 0, 20);
        PageResponse<AdminReviewItem> byRestaurant = queries.reviews(admin(), first.getId(), null, 0, 20);
        PageResponse<AdminReviewItem> byMember = queries.reviews(admin(), null, other.getId(), 0, 20);

        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.content().get(0).content()).isEqualTo("B1");
        assertThat(byRestaurant.content()).extracting(AdminReviewItem::content).containsExactly("A2", "A1");
        assertThat(byMember.content()).extracting(AdminReviewItem::content).containsExactly("B1", "A2");
        assertThat(all.content().get(0).author().email()).isEqualTo("adm-other@example.com");
    }

    // 회원

    @Test
    void 관리자_회원_목록은_검색과_집계와_연동_여부를_준다() {
        newRestaurant("회원 맛집", "부산 해운대구", owner, korean);
        reviewService.create(newRestaurant("다른 맛집", "부산 A", other, korean).getId(), owner.getId(),
                new ReviewRequest(new BigDecimal("5"), "리뷰"));

        PageResponse<AdminMemberItem> byEmail = queries.members(admin(), "adm-owner", 0, 20);
        PageResponse<AdminMemberItem> none = queries.members(admin(), "100%", 0, 20);

        assertThat(byEmail.content()).hasSize(1);
        AdminMemberItem ownerRow = byEmail.content().get(0);
        assertThat(ownerRow.restaurantCount()).isEqualTo(1);
        assertThat(ownerRow.reviewCount()).isEqualTo(1);
        assertThat(ownerRow.googleLinked()).isTrue();
        assertThat(ownerRow.role()).isEqualTo("USER");
        assertThat(none.content()).isEmpty();
    }

    @Test
    void 관리자_회원_목록은_닉네임으로도_찾고_LIKE_특수문자를_문자로_취급한다() {
        members.save(Member.create("adm-under_score@example.com", "h", "밑줄닉", "adm-sub-under"));

        assertThat(queries.members(admin(), "밑줄", 0, 20).content()).hasSize(1);
        assertThat(queries.members(admin(), "_", 0, 20).content()).extracting(AdminMemberItem::nickname)
                .containsOnly("밑줄닉");
    }

    @Test
    void 관리자_회원_상세는_집계를_주고_없는_회원은_MEMBER_NOT_FOUND다() {
        newRestaurant("상세 맛집", "부산 해운대구", owner, korean);

        AdminMemberItem detail = queries.member(admin(), owner.getId());

        assertThat(detail.restaurantCount()).isEqualTo(1);
        assertThat(detail.role()).isEqualTo("USER");
        assertCode(() -> queries.member(admin(), 987_654_321L), ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 회원_조회도_USER에게는_거부된다() {
        assertCode(() -> queries.members(actor(owner.getId(), "USER"), null, 0, 20), ErrorCode.FORBIDDEN);
    }

    // 대시보드

    @Test
    void 대시보드_최근_7일은_서비스_시간대_기준_오늘_포함_7일이다() {
        Restaurant inside = newRestaurant("안쪽", "부산 A", owner, korean);
        Restaurant outside = newRestaurant("바깥", "부산 B", owner, korean);
        jdbc.update("UPDATE restaurant SET created_at = '2026-11-21 00:00:00' WHERE id = ?", inside.getId());
        jdbc.update("UPDATE restaurant SET created_at = '2026-11-20 23:59:59' WHERE id = ?", outside.getId());

        Dashboard dashboard = queries.dashboard(admin());

        assertThat(dashboard.restaurantCount()).isEqualTo(2);
        assertThat(dashboard.newRestaurantsLast7Days()).isEqualTo(1);
        assertThat(dashboard.reviewCount()).isZero();
        assertThat(dashboard.pendingStorageDeletionCount()).isZero();
    }

    // S3 재시도

    @Test
    void S3_재시도는_성공한_행을_지우고_실패한_행의_횟수와_오류를_갱신한다() {
        jdbc.update("INSERT INTO storage_delete_task (s3_key) VALUES ('ok-key'), ('bad-key')");
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 503).")).when(storage).delete("bad-key");

        RetryResult result = retry.retry(admin());

        assertThat(result).isEqualTo(new RetryResult(2, 1, 1));
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task WHERE s3_key = 'ok-key'")).isZero();
        assertThat(jdbc.queryForObject("SELECT retry_count FROM storage_delete_task WHERE s3_key = 'bad-key'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_error FROM storage_delete_task WHERE s3_key = 'bad-key'",
                String.class)).contains("503");
    }

    @Test
    void S3_재시도_대기_목록은_오래된_순이고_안전_메시지만_보인다() {
        jdbc.update("INSERT INTO storage_delete_task (s3_key, retry_count, last_error) VALUES ('first', 2, '실패 A')");
        jdbc.update("INSERT INTO storage_delete_task (s3_key) VALUES ('second')");

        PageResponse<StorageDeletionItem> page = queries.storageDeletions(admin(), 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(StorageDeletionItem::s3Key).containsExactly("first", "second");
        assertThat(page.content().get(0).retryCount()).isEqualTo(2);
        assertThat(page.content().get(0).lastError()).isEqualTo("실패 A");
    }

    // 관리자 이미지

    @Test
    void 관리자는_타인_맛집에_사진을_올리고_지울_수_있다() {
        Restaurant target = newRestaurant("사진 대상", "부산 해운대구", owner, korean);
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03, 0x04};
        var uploaded = imageService.uploadAsAdmin(target.getId(), admin(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg));
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", target.getId())).isEqualTo(1);

        var deleted = imageService.deleteAsAdmin(target.getId(), uploaded.id(), admin());

        assertThat(deleted.storageDeleted()).isTrue();
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", target.getId())).isZero();
    }

    @Test
    void 일반_회원은_타인_맛집에_사진을_올릴_수_없다() {
        Restaurant target = newRestaurant("사진 권한", "부산 해운대구", owner, korean);
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertCode(() -> imageService.upload(target.getId(), other.getId(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg)), ErrorCode.FORBIDDEN);
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", target.getId())).isZero();
    }

    @Test
    void 관리자_업로드도_맛집당_사진_5장_제한을_지킨다() {
        Restaurant target = newRestaurant("개수 제한", "부산 해운대구", owner, korean);
        for (int i = 0; i < 5; i++) {
            jdbc.update("INSERT INTO restaurant_image (restaurant_id, s3_key, original_filename, content_type, file_size, sort_order) "
                    + "VALUES (?, ?, 'a.jpg', 'image/jpeg', 10, ?)", target.getId(), "k-" + i, i);
        }
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertCode(() -> imageService.uploadAsAdmin(target.getId(), admin(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg)), ErrorCode.IMAGE_LIMIT_EXCEEDED);
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", target.getId())).isEqualTo(5);
    }
}
