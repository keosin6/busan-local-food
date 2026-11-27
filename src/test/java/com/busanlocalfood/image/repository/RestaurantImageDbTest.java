package com.busanlocalfood.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.ImageStorageConfig;
import com.busanlocalfood.image.domain.RestaurantImage;
import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.ImageStorage.StorageException;
import com.busanlocalfood.image.service.ImageUrls;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.image.service.StorageCleanup;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.CategoryRepository;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.List;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 이미지의 MySQL 동작 (SQL 001~005 적용 후). 메타데이터 저장과 순서, 개수 제한, 맛집 삭제 시 이미지 행과
 * storage_delete_task 기록, S3 삭제 성공·실패, 보상 삭제, 작성자 확인을 확인한다. S3는 목이다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다. 테스트 전용 DB에서만 실행한다.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        // 공개 URL 조합을 확인할 수 있도록 설정을 넣는다. 저장소는 목으로 바뀌므로 AWS에 접속하지 않는다.
        "app.aws.s3.region=ap-northeast-2",
        "app.aws.s3.bucket=img-test-bucket"
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@Import({RestaurantService.class, RestaurantImageService.class, StorageCleanup.class, ImageUrls.class,
        ImageStorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RestaurantImageDbTest {

    @MockitoBean
    private ImageStorage storage;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantImageService imageService;

    @Autowired
    private RestaurantImageRepository images;

    @Autowired
    private StorageDeleteTaskRepository tasks;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Member owner;
    private Long restaurantId;

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private static MultipartFile jpegFile(String name) {
        return new MockMultipartFile("file", name, "image/jpeg", jpeg());
    }

    @BeforeEach
    void setUp() {
        cleanRows();
        owner = members.save(Member.create("img-owner@example.com", "hash", "이미지작성자", "img-sub-owner"));
        Long koreanId = categories.findAllByOrderBySortOrderAscIdAsc().get(0).getId();
        restaurantId = restaurantService.create(owner.getId(),
                new RestaurantRequest("이미지 맛집", koreanId, "부산 해운대구", null, "설명", null, null)).id();
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
                .filter(m -> m.getEmail().startsWith("img-"))
                .forEach(members::delete);
    }

    @Test
    void 업로드한_이미지_메타데이터가_저장되고_순서대로_조회된다() {
        imageService.upload(restaurantId, owner.getId(), jpegFile("first.jpg"));
        imageService.upload(restaurantId, owner.getId(), jpegFile("second.jpg"));

        List<RestaurantImage> stored = images.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId);

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(RestaurantImage::getSortOrder).containsExactly(0, 1);
        assertThat(stored).extracting(RestaurantImage::getOriginalFilename).containsExactly("first.jpg", "second.jpg");
        assertThat(stored.get(0).getContentType()).isEqualTo("image/jpeg");
        assertThat(stored.get(0).getFileSize()).isEqualTo(jpeg().length);
        assertThat(stored.get(0).getS3Key()).startsWith("restaurants/" + restaurantId + "/").endsWith(".jpg");
        assertThat(stored.get(0).getS3Key()).isNotEqualTo(stored.get(1).getS3Key());
    }

    @Test
    void 맛집당_이미지는_5장까지만_저장되고_여섯_번째는_거부되며_객체도_올리지_않는다() {
        for (int i = 0; i < 5; i++) {
            imageService.upload(restaurantId, owner.getId(), jpegFile("p" + i + ".jpg"));
        }

        assertThatThrownBy(() -> imageService.upload(restaurantId, owner.getId(), jpegFile("p5.jpg")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED));
        assertThat(images.countByRestaurantId(restaurantId)).isEqualTo(5);
        verify(storage, times(5)).put(anyString(), any(), any());
    }

    @Test
    void 대표_이미지는_순서가_가장_작은_이미지이고_목록_썸네일로_나온다() {
        ImageSummary first = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        imageService.upload(restaurantId, owner.getId(), jpegFile("b.jpg"));

        PageResponse<RestaurantSummary> page = restaurantService.list(null, null, "latest", 0, 12);
        RestaurantSummary summary = page.content().stream()
                .filter(r -> r.id().equals(restaurantId)).findFirst().orElseThrow();

        assertThat(summary.thumbnailUrl()).isEqualTo(first.url());
        assertThat(summary.thumbnailUrl()).startsWith("https://img-test-bucket.s3.ap-northeast-2.amazonaws.com/");
    }

    @Test
    void 첫_이미지를_지운_뒤_새_이미지는_최대_순서_다음에_붙는다() {
        ImageSummary first = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        imageService.upload(restaurantId, owner.getId(), jpegFile("b.jpg"));
        imageService.delete(restaurantId, first.id(), owner.getId());

        imageService.upload(restaurantId, owner.getId(), jpegFile("c.jpg"));

        List<RestaurantImage> stored = images.findByRestaurantIdOrderBySortOrderAscIdAsc(restaurantId);
        assertThat(stored).extracting(RestaurantImage::getOriginalFilename).containsExactly("b.jpg", "c.jpg");
        assertThat(stored).extracting(RestaurantImage::getSortOrder).containsExactly(1, 2);
    }

    @Test
    void 맛집_삭제는_이미지_행을_지우고_S3_삭제가_성공하면_추적_기록도_지운다() {
        imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        imageService.upload(restaurantId, owner.getId(), jpegFile("b.jpg"));

        DeleteResult result = restaurantService.delete(restaurantId, owner.getId());

        assertThat(result.deletedImageCount()).isEqualTo(2);
        assertThat(result.storageDeleteFailedCount()).isZero();
        assertThat(restaurants.findById(restaurantId)).isEmpty();
        assertThat(images.countByRestaurantId(restaurantId)).isZero();
        assertThat(tasks.count()).isZero();
        verify(storage, times(2)).delete(anyString());
    }

    @Test
    void 맛집_삭제에서_S3_삭제가_실패하면_DB_삭제는_유지되고_추적_기록이_남는다() {
        imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 503).")).when(storage).delete(anyString());

        DeleteResult result = restaurantService.delete(restaurantId, owner.getId());

        assertThat(result.deletedImageCount()).isEqualTo(1);
        assertThat(result.storageDeleteFailedCount()).isEqualTo(1);
        assertThat(restaurants.findById(restaurantId)).isEmpty();
        assertThat(images.count()).isZero();
        List<StorageDeleteTask> remaining = tasks.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getRetryCount()).isEqualTo(1);
        assertThat(remaining.get(0).getLastError()).contains("HTTP 503");
    }

    @Test
    void 작성자가_아니면_맛집_삭제가_거부되고_이미지와_기록과_S3는_그대로다() {
        ImageSummary image = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        Member other = members.save(Member.create("img-other@example.com", "hash", "다른회원", "img-sub-other"));

        assertThatThrownBy(() -> restaurantService.delete(restaurantId, other.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(restaurants.findById(restaurantId)).isPresent();
        assertThat(images.findById(image.id())).isPresent();
        assertThat(tasks.count()).isZero();
        verify(storage, times(1)).put(anyString(), any(), any());
        verify(storage, times(0)).delete(anyString());
    }

    @Test
    void 업로드_도중_맛집이_사라지면_올린_객체를_보상으로_지우고_이미지_행은_남지_않는다() {
        doAnswer(invocation -> {
            restaurants.deleteById(restaurantId);
            return null;
        }).when(storage).put(anyString(), any(), any());

        assertThatThrownBy(() -> imageService.upload(restaurantId, owner.getId(), jpegFile("gone.jpg")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));

        assertThat(images.count()).isZero();
        verify(storage, times(1)).delete(anyString());
        assertThat(tasks.count()).isZero();
    }

    @Test
    void 보상_삭제까지_실패하면_storage_delete_task에_키가_남는다() {
        doAnswer(invocation -> {
            restaurants.deleteById(restaurantId);
            return null;
        }).when(storage).put(anyString(), any(), any());
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 500).")).when(storage).delete(anyString());

        assertThatThrownBy(() -> imageService.upload(restaurantId, owner.getId(), jpegFile("gone.jpg")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));

        assertThat(images.count()).isZero();
        List<StorageDeleteTask> remaining = tasks.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getS3Key()).startsWith("restaurants/" + restaurantId + "/");
        assertThat(remaining.get(0).getRetryCount()).isEqualTo(1);
    }

    @Test
    void 이미지_삭제는_행을_지우고_S3_성공하면_추적_기록도_지운다() {
        ImageSummary image = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));

        ImageDeleteResult result = imageService.delete(restaurantId, image.id(), owner.getId());

        assertThat(result.storageDeleted()).isTrue();
        assertThat(images.findById(image.id())).isEmpty();
        assertThat(tasks.count()).isZero();
    }

    @Test
    void 이미지_삭제의_S3_실패는_storageDeleted_false와_추적_기록으로_남는다() {
        ImageSummary image = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 503).")).when(storage).delete(anyString());

        ImageDeleteResult result = imageService.delete(restaurantId, image.id(), owner.getId());

        assertThat(result.storageDeleted()).isFalse();
        assertThat(images.findById(image.id())).isEmpty();
        assertThat(tasks.count()).isEqualTo(1);
    }

    @Test
    void 다른_맛집의_이미지는_이_맛집_경로로_지울_수_없다() {
        ImageSummary image = imageService.upload(restaurantId, owner.getId(), jpegFile("a.jpg"));
        Long otherId = restaurantService.create(owner.getId(), new RestaurantRequest("다른 맛집",
                categories.findAllByOrderBySortOrderAscIdAsc().get(0).getId(), "부산", null, "설명", null, null)).id();

        assertThatThrownBy(() -> imageService.delete(otherId, image.id(), owner.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
        assertThat(images.findById(image.id())).isPresent();
        verify(storage, times(0)).delete(anyString());
    }
}
