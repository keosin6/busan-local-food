package com.busanlocalfood.image.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * S3 삭제 추적 행 (DATABASE_ERD 2.9). "DB에서는 이미 지워졌지만 S3 객체는 아직 지우지 못한 키"를 뜻한다.
 * S3 삭제에 성공하면 행을 지운다. 실패하면 retry_count를 올리고 last_error를 남긴다.
 * last_error에는 자격 증명, 서명 값, 요청 헤더가 들어가지 않는 짧은 메시지만 저장한다.
 */
@Entity
@Table(name = "storage_delete_task")
public class StorageDeleteTask {

    private static final int LAST_ERROR_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "s3_key", nullable = false, length = 500, unique = true)
    private String s3Key;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = LAST_ERROR_MAX)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StorageDeleteTask() {
        // JPA 기본 생성자
    }

    private StorageDeleteTask(String s3Key) {
        this.s3Key = s3Key;
        this.retryCount = 0;
    }

    public static StorageDeleteTask create(String s3Key) {
        return new StorageDeleteTask(s3Key);
    }

    /** S3 삭제 실패를 기록한다. 메시지는 호출자가 비밀 정보를 걸러 둔 값이어야 한다. */
    public void recordFailure(String safeMessage) {
        this.retryCount = this.retryCount + 1;
        String message = safeMessage == null ? "" : safeMessage;
        this.lastError = message.length() > LAST_ERROR_MAX ? message.substring(0, LAST_ERROR_MAX) : message;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getS3Key() { return s3Key; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
