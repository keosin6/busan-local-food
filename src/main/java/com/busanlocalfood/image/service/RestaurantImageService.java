package com.busanlocalfood.image.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.domain.RestaurantImage;
import com.busanlocalfood.image.repository.RestaurantImageRepository;
import com.busanlocalfood.image.service.ImagePolicy.ValidatedImage;
import com.busanlocalfood.image.service.ImageStorage.StorageException;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 맛집 이미지 업로드와 삭제 (FUNCTION_SPEC F1-03, API_SPEC 5.1·5.2). 작성자 본인만 쓸 수 있다.
 * 관리자 권한은 여기에 섞지 않는다. 관리자 이미지 API는 F1-09 범위다.
 *
 * 업로드 순서: 작성자·개수 확인 → 파일 검증 → S3 업로드 → DB 저장(맛집 행을 잠가 개수를 다시 셈).
 *   S3 업로드 실패면 DB에 아무것도 쓰지 않고 502를 낸다.
 *   DB 저장이 실패하면 업로드한 객체를 지운다. 지우기도 실패하면 storage_delete_task에 남긴다.
 *
 * 삭제 순서: DB 트랜잭션(이미지 행 삭제 + storage_delete_task 기록) → 커밋 → S3 삭제.
 *   S3 삭제 실패는 DB 삭제 실패가 아니다. 응답의 storageDeleted가 false가 되고 키는 storage_delete_task에 남는다.
 */
@Service
public class RestaurantImageService {

    public static final int MAX_IMAGES_PER_RESTAURANT = 5;

    private final RestaurantRepository restaurants;
    private final RestaurantImageRepository images;
    private final ImageStorage storage;
    private final StorageCleanup cleanup;
    private final ImageUrls imageUrls;
    private final TransactionTemplate tx;

    public RestaurantImageService(RestaurantRepository restaurants, RestaurantImageRepository images,
                                  ImageStorage storage, StorageCleanup cleanup, ImageUrls imageUrls,
                                  PlatformTransactionManager transactionManager) {
        this.restaurants = restaurants;
        this.images = images;
        this.storage = storage;
        this.cleanup = cleanup;
        this.imageUrls = imageUrls;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public ImageSummary upload(long restaurantId, long memberId, MultipartFile file) {
        Restaurant restaurant = findRestaurant(restaurantId);
        requireOwner(restaurant, memberId);
        requireRoomForImage(restaurantId);

        ValidatedImage image = ImagePolicy.validate(file);
        String s3Key = newKey(restaurantId, image);
        try {
            storage.put(s3Key, image.type().contentType(), image.bytes());
        } catch (StorageException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        try {
            return tx.execute(status -> saveMetadata(restaurantId, memberId, s3Key, image));
        } catch (RuntimeException e) {
            compensateUpload(s3Key);
            throw e;
        }
    }

    public ImageDeleteResult delete(long restaurantId, long imageId, long memberId) {
        String s3Key = tx.execute(status -> {
            // 맛집 행을 잠가 맛집 전체 삭제와 직렬화한다. 맛집이 먼저 지워졌으면 여기서 RESTAURANT_NOT_FOUND가 된다.
            Restaurant restaurant = restaurants.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
            requireOwner(restaurant, memberId);
            RestaurantImage image = images.findByIdAndRestaurantId(imageId, restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
            cleanup.recordPending(image.getS3Key());
            images.delete(image);
            return image.getS3Key();
        });
        boolean storageDeleted = cleanup.deleteAfterCommit(s3Key);
        return new ImageDeleteResult(imageId, storageDeleted);
    }

    /** 맛집 행을 잠근 뒤 다시 확인하고 저장한다. 동시에 올라온 요청도 5장을 넘지 못한다. */
    private ImageSummary saveMetadata(long restaurantId, long memberId, String s3Key, ValidatedImage image) {
        Restaurant locked = restaurants.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        requireOwner(locked, memberId);
        requireRoomForImage(restaurantId);

        int sortOrder = images.maxSortOrder(restaurantId) + 1;
        RestaurantImage saved = images.saveAndFlush(RestaurantImage.create(locked, s3Key,
                image.originalFilename(), image.type().contentType(), image.bytes().length, sortOrder));
        return new ImageSummary(saved.getId(), imageUrls.urlOf(s3Key), sortOrder);
    }

    /** DB 저장이 실패했으므로 올린 객체를 지운다. 지우기도 실패하면 추적 기록을 남긴다. */
    private void compensateUpload(String s3Key) {
        try {
            storage.delete(s3Key);
        } catch (StorageException e) {
            cleanup.recordFailureInNewTransaction(s3Key, e.safeMessage());
        }
    }

    private void requireRoomForImage(long restaurantId) {
        if (images.countByRestaurantId(restaurantId) >= MAX_IMAGES_PER_RESTAURANT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }
    }

    private Restaurant findRestaurant(long restaurantId) {
        return restaurants.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    private static void requireOwner(Restaurant restaurant, long memberId) {
        if (!restaurant.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static String newKey(long restaurantId, ValidatedImage image) {
        return "restaurants/" + restaurantId + "/" + UUID.randomUUID() + "." + image.type().canonicalExtension();
    }
}
