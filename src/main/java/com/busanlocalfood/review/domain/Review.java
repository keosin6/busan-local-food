package com.busanlocalfood.review.domain;

import com.busanlocalfood.member.domain.Member;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 리뷰 Entity (DATABASE_ERD 2.5). 한 회원은 한 맛집에 리뷰를 하나만 쓴다 (uk_review_member_restaurant).
 * 평점 1~5 정수, 내용 1~500자는 호출자(ReviewInputRules)가 검증한 뒤 만든다.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Review() {
        // JPA 기본 생성자
    }

    private Review(Restaurant restaurant, Member member, int rating, String content) {
        this.restaurant = restaurant;
        this.member = member;
        this.rating = rating;
        this.content = content;
    }

    public static Review create(Restaurant restaurant, Member member, int rating, String content) {
        return new Review(restaurant, member, rating, content);
    }

    public boolean isWrittenBy(long memberId) {
        return member != null && member.getId() != null && member.getId() == memberId;
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
    public Restaurant getRestaurant() { return restaurant; }
    public Member getMember() { return member; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
