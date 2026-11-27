package com.busanlocalfood.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.config.AwsS3Properties;
import com.busanlocalfood.image.domain.RestaurantImage;
import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.repository.RestaurantImageRepository;
import com.busanlocalfood.image.repository.StorageDeleteTaskRepository;
import com.busanlocalfood.image.service.ImageStorage.StorageException;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.restaurant.domain.Category;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 이미지 업로드·삭제 서비스 (FUNCTION_SPEC F1-03). 저장소와 DB는 목이다. 트랜잭션 관리자도 목이므로
 * 여기서는 호출 순서와 보상·기록 분기만 확인한다. 실제 트랜잭션 동작은 RestaurantImageDbTest가 본다.
 */
class RestaurantImageServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long RESTAURANT_ID = 10L;
    private static final long IMAGE_ID = 100L;

    private RestaurantRepository restaurants;
    private RestaurantImageRepository images;
    private StorageDeleteTaskRepository tasks;
    private ImageStorage storage;
    private RestaurantImageService service;
    private Restaurant restaurant;

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private static MultipartFile jpegFile() {
        return new MockMultipartFile("file", "food.jpg", "image/jpeg", jpeg());
    }

    @BeforeEach
    void setUp() {
        restaurants = mock(RestaurantRepository.class);
        images = mock(RestaurantImageRepository.class);
        tasks = mock(StorageDeleteTaskRepository.class);
        storage = mock(ImageStorage.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        StorageCleanup cleanup = new StorageCleanup(tasks, storage, transactionManager);
        ImageUrls imageUrls = new ImageUrls(new AwsS3Properties("ap-northeast-2", "test-bucket"));
        service = new RestaurantImageService(restaurants, images, storage, cleanup, imageUrls, transactionManager);

        Member owner = Member.create("owner@example.com", "hash", "작성자", "google-sub-owner");
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        Category category = mock(Category.class);
        restaurant = Restaurant.create(owner, category,
                new Restaurant.RestaurantFields("맛집", "주소", null, "설명", null, null));
        ReflectionTestUtils.setField(restaurant, "id", RESTAURANT_ID);

        // 저장소 쿼리는 이미지가 없을 때 -1을 돌려준다 (coalesce(max, -1)). Mockito 기본값 0을 그대로 쓰지 않는다.
        when(images.maxSortOrder(RESTAURANT_ID)).thenReturn(-1);
        when(restaurants.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(restaurants.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(images.saveAndFlush(any(RestaurantImage.class))).thenAnswer(inv -> {
            RestaurantImage saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", IMAGE_ID);
            return saved;
        });
    }

    private RestaurantImage imageOf(String s3Key) {
        RestaurantImage image = RestaurantImage.create(restaurant, s3Key, "food.jpg", "image/jpeg", 8, 0);
        ReflectionTestUtils.setField(image, "id", IMAGE_ID);
        return image;
    }

    @Test
    void 작성자가_올리면_객체를_올리고_이미지_행을_저장하고_요약을_돌려준다() {
        ImageSummary summary = service.upload(RESTAURANT_ID, OWNER_ID, jpegFile());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq("image/jpeg"), any(byte[].class));
        assertThat(key.getValue()).startsWith("restaurants/10/").endsWith(".jpg");

        ArgumentCaptor<RestaurantImage> saved = ArgumentCaptor.forClass(RestaurantImage.class);
        verify(images).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("food.jpg");
        assertThat(saved.getValue().getFileSize()).isEqualTo(jpeg().length);
        assertThat(saved.getValue().getSortOrder()).isZero();
        assertThat(saved.getValue().getS3Key()).isEqualTo(key.getValue());

        assertThat(summary.id()).isEqualTo(IMAGE_ID);
        assertThat(summary.sortOrder()).isZero();
        assertThat(summary.url()).isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key.getValue());
    }

    @Test
    void 새_이미지는_기존_최대_순서_다음에_붙는다() {
        when(images.maxSortOrder(RESTAURANT_ID)).thenReturn(3);

        ImageSummary summary = service.upload(RESTAURANT_ID, OWNER_ID, jpegFile());

        assertThat(summary.sortOrder()).isEqualTo(4);
    }

    @Test
    void 작성자가_아니면_FORBIDDEN이고_객체를_올리지_않는다() {
        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OTHER_ID, jpegFile()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void 없는_맛집이면_RESTAURANT_NOT_FOUND다() {
        when(restaurants.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(99L, OWNER_ID, jpegFile()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void 이미_5장이면_IMAGE_LIMIT_EXCEEDED이고_객체를_올리지_않는다() {
        when(images.countByRestaurantId(RESTAURANT_ID)).thenReturn(5L);

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, jpegFile()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED));
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void 잠금_안에서_개수가_넘으면_올린_객체를_지우고_IMAGE_LIMIT_EXCEEDED를_낸다() {
        // 사전 확인은 4장이었지만, 잠금 안에서 다른 요청이 한 장을 먼저 저장해 5장이 되었다.
        when(images.countByRestaurantId(RESTAURANT_ID)).thenReturn(4L, 5L);

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, jpegFile()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED));
        verify(images, never()).saveAndFlush(any());
        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(storage).delete(deleted.capture());
        assertThat(deleted.getValue()).startsWith("restaurants/10/");
    }

    @Test
    void 허용되지_않는_형식이면_IMAGE_TYPE_NOT_ALLOWED이고_객체를_올리지_않는다() {
        MultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", jpeg());

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, gif))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TYPE_NOT_ALLOWED));
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void 파일_크기가_초과되면_IMAGE_TOO_LARGE이고_객체를_올리지_않는다() {
        byte[] big = new byte[(int) ImagePolicy.MAX_BYTES + 1];
        System.arraycopy(jpeg(), 0, big, 0, 4);
        MultipartFile tooLarge = new MockMultipartFile("file", "a.jpg", "image/jpeg", big);

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, tooLarge))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE));
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void S3_업로드가_실패하면_DB에_아무것도_쓰지_않고_STORAGE_ERROR다() {
        doThrow(new StorageException("S3 업로드가 실패했습니다 (HTTP 503).")).when(storage).put(any(), any(), any());

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, jpegFile()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
        verify(images, never()).saveAndFlush(any());
        verify(storage, never()).delete(any());
        verify(tasks, never()).save(any());
    }

    @Test
    void DB_저장이_실패하면_올린_객체를_보상으로_지운다() {
        when(images.saveAndFlush(any(RestaurantImage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, jpegFile()))
                .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<String> putKey = ArgumentCaptor.forClass(String.class);
        verify(storage).put(putKey.capture(), any(), any());
        verify(storage).delete(eq(putKey.getValue()));
        verify(tasks, never()).save(any());
    }

    @Test
    void 보상_삭제도_실패하면_storage_delete_task에_키와_재시도_횟수를_남긴다() {
        when(images.saveAndFlush(any(RestaurantImage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 500).")).when(storage).delete(any());

        assertThatThrownBy(() -> service.upload(RESTAURANT_ID, OWNER_ID, jpegFile()))
                .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<StorageDeleteTask> saved = ArgumentCaptor.forClass(StorageDeleteTask.class);
        verify(tasks).save(saved.capture());
        assertThat(saved.getValue().getS3Key()).startsWith("restaurants/10/");
        assertThat(saved.getValue().getRetryCount()).isEqualTo(1);
        assertThat(saved.getValue().getLastError()).contains("HTTP 500");
    }

    @Test
    void 작성자는_이미지를_지우면_DB_행과_추적_기록을_만들고_커밋_뒤_S3를_지운다() {
        String key = "restaurants/10/a.jpg";
        when(images.findByIdAndRestaurantId(IMAGE_ID, RESTAURANT_ID)).thenReturn(Optional.of(imageOf(key)));
        StorageDeleteTask task = StorageDeleteTask.create(key);
        // 1번째 조회: 기록 생성 전 없음, 2번째 조회: S3 삭제 성공 뒤 기록 제거
        when(tasks.findByS3Key(key)).thenReturn(Optional.empty(), Optional.of(task));

        ImageDeleteResult result = service.delete(RESTAURANT_ID, IMAGE_ID, OWNER_ID);

        assertThat(result.storageDeleted()).isTrue();
        assertThat(result.imageId()).isEqualTo(IMAGE_ID);
        verify(images).delete(any(RestaurantImage.class));
        verify(storage).delete(key);
        verify(tasks).save(any(StorageDeleteTask.class));
        verify(tasks).delete(task);
    }

    @Test
    void 이미지_S3_삭제가_실패하면_DB_삭제는_유지되고_기록이_남고_storageDeleted는_false다() {
        String key = "restaurants/10/b.jpg";
        when(images.findByIdAndRestaurantId(IMAGE_ID, RESTAURANT_ID)).thenReturn(Optional.of(imageOf(key)));
        when(tasks.findByS3Key(key)).thenReturn(Optional.empty(), Optional.empty());
        doThrow(new StorageException("S3 삭제가 실패했습니다 (HTTP 503).")).when(storage).delete(key);

        ImageDeleteResult result = service.delete(RESTAURANT_ID, IMAGE_ID, OWNER_ID);

        assertThat(result.storageDeleted()).isFalse();
        verify(images).delete(any(RestaurantImage.class));
        ArgumentCaptor<StorageDeleteTask> saved = ArgumentCaptor.forClass(StorageDeleteTask.class);
        verify(tasks, times(2)).save(saved.capture());
        StorageDeleteTask last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(last.getRetryCount()).isEqualTo(1);
        assertThat(last.getLastError()).contains("HTTP 503");
    }

    @Test
    void 작성자가_아니면_이미지_삭제는_FORBIDDEN이고_S3에_아무것도_하지_않는다() {
        assertThatThrownBy(() -> service.delete(RESTAURANT_ID, IMAGE_ID, OTHER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(storage, never()).delete(any());
        verify(images, never()).delete(any(RestaurantImage.class));
    }

    @Test
    void 다른_맛집의_이미지_ID로_지우면_RESTAURANT_NOT_FOUND다() {
        when(images.findByIdAndRestaurantId(IMAGE_ID, RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(RESTAURANT_ID, IMAGE_ID, OWNER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND));
        verify(storage, never()).delete(any());
    }
}
