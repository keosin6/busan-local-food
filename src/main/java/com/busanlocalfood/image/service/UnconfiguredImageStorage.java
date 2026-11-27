package com.busanlocalfood.image.service;

/**
 * S3 설정(AWS_REGION, AWS_S3_BUCKET)이 없을 때 쓰는 구현. 항상 실패한다.
 * 가짜 성공을 돌려주지 않는다. 삭제 실패는 storage_delete_task에 남는다.
 */
public final class UnconfiguredImageStorage implements ImageStorage {

    private static final String MESSAGE =
            "S3 설정이 없어 이미지 저장소를 사용할 수 없습니다. AWS_REGION과 AWS_S3_BUCKET을 확인해 주세요.";

    @Override
    public void put(String s3Key, String contentType, byte[] content) {
        throw new StorageException(MESSAGE);
    }

    @Override
    public void delete(String s3Key) {
        throw new StorageException(MESSAGE);
    }
}
