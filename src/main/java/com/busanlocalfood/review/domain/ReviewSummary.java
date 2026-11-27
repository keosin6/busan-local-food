package com.busanlocalfood.review.domain;

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
 * AI 리뷰 요약 캐시 (DATABASE_ERD 2.7, SQL 007). 맛집당 최대 한 행이다.
 * 행이 있으면 요약이 유효하다. 리뷰 작성·삭제 트랜잭션이 행을 지운다.
 */
@Entity
@Table(name = "ai_review_summary")
public class ReviewSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_review_count", nullable = false)
    private int sourceReviewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ReviewSummary() {
        // JPA 기본 생성자
    }

    private ReviewSummary(Restaurant restaurant, String summary, int sourceReviewCount) {
        this.restaurant = restaurant;
        this.summary = summary;
        this.sourceReviewCount = sourceReviewCount;
    }

    /** 검증을 통과한 요약 본문만 넣는다. 리뷰가 3개 미만인 맛집은 호출하지 않는다. */
    public static ReviewSummary create(Restaurant restaurant, String summary, int sourceReviewCount) {
        return new ReviewSummary(restaurant, summary, sourceReviewCount);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getSummary() { return summary; }
    public int getSourceReviewCount() { return sourceReviewCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
