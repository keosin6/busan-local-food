package com.busanlocalfood.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 삭제와 이미지 업로드·삭제의 동시성 검증 (실제 MySQL 8, SQL 001~005 적용).
 *
 * 확인하는 것: 맛집 행 잠금이 직렬화를 보장하는지, 커밋 전 이미지가 목록에서 빠지지 않는지,
 * S3 객체가 고아로 남지 않는지, 삭제 트랜잭션이 롤백되면 S3에 손대지 않는지.
 * 스레드 타이밍은 무작위지만 검사하는 불변식은 어떤 순서에서도 같아야 한다.
 * S3 저장소는 목이다. 실제 버킷에는 접속하지 않는다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=concurrency-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RestaurantService.class, RestaurantImageService.class, StorageCleanup.class, ImageUrls.class,
        ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RestaurantDeleteConcurrencyDbTest {

    private static final int ROUNDS = 25;
    private static final long WAIT_SECONDS = 20;

    @MockitoBean
    private ImageStorage storage;

    /** 실제 동작을 그대로 두고, 롤백 시나리오에서만 특정 호출을 실패시킨다. */
    @MockitoSpyBean
    private StorageCleanup cleanup;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantImageService imageService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Long koreanId;

    /** 저장소에 올라간 키와 삭제 요청된 키. 여러 스레드에서 쓰므로 동시성 안전한 컬렉션을 쓴다. */
    private final Set<String> putKeys = ConcurrentHashMap.newKeySet();
    private final List<String> deleteCalls = new CopyOnWriteArrayList<>();

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private static MultipartFile jpegFile(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", jpeg());
    }

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("conc-owner@example.com", "hash", "동시성작성자", "conc-sub-owner"));
        koreanId = categories.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        putKeys.clear();
        deleteCalls.clear();
        doAnswer(invocation -> {
            putKeys.add(invocation.getArgument(0));
            return null;
        }).when(storage).put(anyString(), any(), any());
        doAnswer(invocation -> {
            deleteCalls.add(invocation.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
    }

    @AfterEach
    void tearDown() {
        cleanRows();
    }

    private void cleanRows() {
        jdbc.update("DELETE FROM storage_delete_task");
        jdbc.update("DELETE FROM restaurant_image");
        restaurants.deleteAll();
        members.findAll().stream()
                .filter(m -> m.getEmail().startsWith("conc-"))
                .forEach(members::delete);
    }

    private Long createRestaurant(String name) {
        return restaurantService.create(owner.getId(),
                new RestaurantRequest(name, koreanId, "부산 해운대구", null, "설명", null, null)).id();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("래치가 시간 안에 풀리지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** 업로드가 맛집 삭제 때문에 실패하는 것(RESTAURANT_NOT_FOUND)은 허용한다. 그 밖의 실패는 테스트 실패다. */
    private static void allowNotFound(Future<?> future) throws Exception {
        try {
            await(future);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException be && be.getErrorCode() == ErrorCode.RESTAURANT_NOT_FOUND) {
                return;
            }
            throw e;
        }
    }

    private long countRows(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    // ---- 1. 잠금이 실제로 기다리게 하는지 ----

    @Test
    void 맛집_삭제는_다른_트랜잭션이_잡은_맛집_행_잠금이_풀릴_때까지_기다린다() throws Exception {
        Long id = createRestaurant("잠금 대기");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                restaurants.findByIdForUpdate(id).orElseThrow();
                locked.countDown();
                awaitLatch(release);
            }));
            assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<DeleteResult> deleting = pool.submit(() -> restaurantService.delete(id, owner.getId()));

            // 잠금이 풀리기 전에는 삭제가 끝나면 안 된다. 일반 조회를 쓰던 예전 구현은 여기서 끝나 버린다.
            assertThatThrownBy(() -> deleting.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThat(restaurants.findById(id)).isPresent();

            release.countDown();
            await(holder);
            assertThat(await(deleting).deletedImageCount()).isZero();
            assertThat(restaurants.findById(id)).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // ---- 2. 업로드와 맛집 삭제 경합: 고아 객체가 없어야 한다 ----

    @Test
    void 이미지_업로드와_맛집_삭제가_동시에_실행돼도_S3_객체가_고아로_남지_않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long id = createRestaurant("업로드 경합 " + round);
            putKeys.clear();
            deleteCalls.clear();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier start = new CyclicBarrier(2);
                Future<ImageSummary> upload = pool.submit(callable(() -> {
                    start.await();
                    return imageService.upload(id, owner.getId(), jpegFile("race.jpg"));
                }));
                Future<DeleteResult> delete = pool.submit(callable(() -> {
                    start.await();
                    return restaurantService.delete(id, owner.getId());
                }));
                allowNotFound(upload);
                await(delete);
            } finally {
                pool.shutdownNow();
            }

            assertNoOrphans(id, "업로드 경합 round " + round);
        }
    }

    /** 업로드가 저장소에는 올렸지만 DB에 쓰기 전에 맛집이 지워진 경우도 보상 삭제로 같은 결과가 나와야 한다. */
    @Test
    void 업로드_중_맛집이_삭제되면_보상으로_객체를_지워_고아가_없다() throws Exception {
        Long id = createRestaurant("보상 경합");
        CountDownLatch putDone = new CountDownLatch(1);
        CountDownLatch deleteDone = new CountDownLatch(1);
        // 객체 업로드가 저장소에 올라간 뒤 DB 단계로 넘어가기 전에 멈춘다. 그 사이 맛집 삭제를 끝내고 나서 풀어 준다.
        doAnswer(invocation -> {
            putKeys.add(invocation.getArgument(0));
            putDone.countDown();
            awaitLatch(deleteDone);
            return null;
        }).when(storage).put(anyString(), any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ImageSummary> upload = pool.submit(callable(() ->
                    imageService.upload(id, owner.getId(), jpegFile("late.jpg"))));
            assertThat(putDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            restaurantService.delete(id, owner.getId());
            deleteDone.countDown();

            assertThatThrownBy(() -> await(upload))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class);
        } finally {
            pool.shutdownNow();
        }

        assertNoOrphans(id, "보상 경합");
        assertThat(putKeys).hasSize(1);
        assertThat(deleteCalls).containsExactlyElementsOf(putKeys);
    }

    // ---- 3. 개별 이미지 삭제와 맛집 전체 삭제 경합: 추적 기록과 S3가 일관되어야 한다 ----

    @Test
    void 이미지_개별_삭제와_맛집_전체_삭제가_동시에_실행돼도_키마다_한_번만_지워지고_기록이_남지_않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long id = createRestaurant("개별 삭제 경합 " + round);
            ImageSummary first = imageService.upload(id, owner.getId(), jpegFile("a.jpg"));
            imageService.upload(id, owner.getId(), jpegFile("b.jpg"));
            List<String> uploaded = new ArrayList<>(putKeys);
            deleteCalls.clear();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> imageDelete = pool.submit(callable(() -> {
                    start.await();
                    return imageService.delete(id, first.id(), owner.getId());
                }));
                Future<?> restaurantDelete = pool.submit(callable(() -> {
                    start.await();
                    return restaurantService.delete(id, owner.getId());
                }));
                allowNotFound(imageDelete);
                await(restaurantDelete);
            } finally {
                pool.shutdownNow();
            }

            assertNoOrphans(id, "개별 삭제 경합 round " + round);
            // 각 키는 정확히 한 번 지워져야 한다. 두 번 지우면 추적 기록과 S3 상태가 어긋난다.
            assertThat(deleteCalls).doesNotHaveDuplicates();
            List<String> sortedDeleted = new ArrayList<>(deleteCalls);
            List<String> sortedUploaded = new ArrayList<>(uploaded);
            Collections.sort(sortedDeleted);
            Collections.sort(sortedUploaded);
            assertThat(sortedDeleted).isEqualTo(sortedUploaded);
            putKeys.clear();
        }
    }

    // ---- 4. 롤백: 맛집 삭제 트랜잭션이 실패하면 S3에 손대지 않는다 ----

    @Test
    void 맛집_삭제_트랜잭션이_중간에_실패하면_S3_객체는_지워지지_않고_행과_기록도_그대로다() throws Exception {
        Long id = createRestaurant("롤백 맛집");
        imageService.upload(id, owner.getId(), jpegFile("a.jpg"));
        imageService.upload(id, owner.getId(), jpegFile("b.jpg"));
        deleteCalls.clear();
        // 첫 키의 추적 기록은 실제로 쓰고, 두 번째 키에서 실패시킨다. 첫 번째 쓰기도 롤백되어야 한다.
        doCallRealMethod().doThrow(new IllegalStateException("주입된 실패"))
                .when(cleanup).recordPending(anyString());

        assertThatThrownBy(() -> restaurantService.delete(id, owner.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(deleteCalls).isEmpty();
        assertThat(restaurants.findById(id)).isPresent();
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", id)).isEqualTo(2);
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task")).isZero();
    }

    @Test
    void 이미지_삭제_트랜잭션이_실패하면_S3_객체는_지워지지_않고_행도_남는다() throws Exception {
        Long id = createRestaurant("이미지 롤백");
        ImageSummary image = imageService.upload(id, owner.getId(), jpegFile("a.jpg"));
        deleteCalls.clear();
        doThrow(new IllegalStateException("주입된 실패")).when(cleanup).recordPending(anyString());

        assertThatThrownBy(() -> imageService.delete(id, image.id(), owner.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(deleteCalls).isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE id = ?", image.id())).isEqualTo(1);
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task")).isZero();
    }

    // ---- 5. 맛집이 사라진 뒤 이미지 삭제는 RESTAURANT_NOT_FOUND이고 기록을 만들지 않는다 ----

    @Test
    void 맛집이_먼저_지워졌으면_이미지_삭제는_RESTAURANT_NOT_FOUND이고_기록을_만들지_않는다() throws Exception {
        Long id = createRestaurant("먼저 삭제");
        ImageSummary image = imageService.upload(id, owner.getId(), jpegFile("a.jpg"));
        restaurantService.delete(id, owner.getId());
        deleteCalls.clear();

        assertThatThrownBy(() -> imageService.delete(id, image.id(), owner.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
        assertThat(deleteCalls).isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task")).isZero();
    }

    // ---- 도우미 ----

    /** 불변식: 맛집과 이미지 행이 모두 없고, 올라간 모든 키가 삭제 요청을 받았으며, 추적 기록이 남지 않는다. */
    private void assertNoOrphans(Long restaurantId, String context) {
        assertThat(restaurants.findById(restaurantId)).as(context + ": 맛집").isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM restaurant_image WHERE restaurant_id = ?", restaurantId))
                .as(context + ": 이미지 행").isZero();
        assertThat(countRows("SELECT COUNT(*) FROM storage_delete_task")).as(context + ": 추적 기록").isZero();
        assertThat(deleteCalls).as(context + ": 삭제 요청된 키").containsAll(putKeys);
    }

    @FunctionalInterface
    private interface ThrowingRunnable<T> {
        T run() throws Exception;
    }

    private static <T> Callable<T> callable(ThrowingRunnable<T> body) {
        return body::run;
    }
}
