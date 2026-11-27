package com.busanlocalfood.image.domain;

import com.busanlocalfood.restaurant.domain.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 맛집 이미지 메타데이터 (DATABASE_ERD 2.4). 실제 파일은 S3에 있고, 이 행은 객체 키와 표시 정보만 가진다.
 * 대표 이미지는 sort_order가 가장 작은 행이다. 원본 파일명은 DB에만 저장한다.
 */
@Entity
@Table(name = "restaurant_image")
public class RestaurantImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "s3_key", nullable = false, length = 500, unique = true)
    private String s3Key;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private int fileSize;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RestaurantImage() {
        // JPA 기본 생성자
    }

    private RestaurantImage(Restaurant restaurant, String s3Key, String originalFilename, String contentType,
                            int fileSize, int sortOrder) {
        this.restaurant = restaurant;
        this.s3Key = s3Key;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
    }

    /** 검증과 S3 업로드가 끝난 뒤에만 만든다. 값의 검증은 호출자가 끝낸 상태여야 한다. */
    public static RestaurantImage create(Restaurant restaurant, String s3Key, String originalFilename,
                                         String contentType, int fileSize, int sortOrder) {
        return new RestaurantImage(restaurant, s3Key, originalFilename, contentType, fileSize, sortOrder);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getS3Key() { return s3Key; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public int getFileSize() { return fileSize; }
    public int getSortOrder() { return sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
