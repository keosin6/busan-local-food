package com.busanlocalfood.image.service;

/**
 * 이미지 객체 저장소 경계. 구현은 S3다 (S3ImageStorage). 테스트는 목으로 바꾼다.
 * 실패는 StorageException으로 알리며, 메시지에는 자격 증명·서명·요청 헤더를 넣지 않는다.
 */
public interface ImageStorage {

    /** 객체를 올린다. 실패하면 StorageException을 던진다. */
    void put(String s3Key, String contentType, byte[] content);

    /**
     * 객체를 지운다. 객체가 이미 없으면 성공으로 본다 (S3 DeleteObject는 없는 키에도 성공한다).
     * 실패하면 StorageException을 던진다.
     */
    void delete(String s3Key);

    /** 저장소 오류. safeMessage()는 화면이나 storage_delete_task에 그대로 써도 되는 짧은 문장이다. */
    final class StorageException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String safeMessage;

        public StorageException(String safeMessage) {
            super(safeMessage);
            this.safeMessage = safeMessage;
        }

        public String safeMessage() {
            return safeMessage;
        }
    }
}
