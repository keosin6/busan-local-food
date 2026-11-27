package com.busanlocalfood.restaurant.domain;

import com.busanlocalfood.member.domain.Member;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 맛집 Entity (DATABASE_ERD 2.3). 작성자는 member_id로 연결하고, 카테고리는 category_id로 연결한다.
 * 평균 평점과 리뷰 수는 리뷰 기능(F1-04)이 갱신한다. 이번 범위에서는 초기값(0.0, 0)만 쓴다.
 * 이미지(F1-03), 찜(F1-06), AI 요약(F1-05) 연관 행은 아직 없다.
 */
@Entity
@Table(name = "restaurant")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "average_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal averageRating = new BigDecimal("0.0");

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Restaurant() {
        // JPA 기본 생성자
    }

    private Restaurant(Member member, Category category, RestaurantFields fields) {
        this.member = member;
        this.category = category;
        apply(fields);
    }

    /** 맛집을 새로 만든다. 작성자는 현재 인증된 회원이다. 값의 검증과 정규화는 호출자가 끝낸 상태여야 한다. */
    public static Restaurant create(Member member, Category category, RestaurantFields fields) {
        return new Restaurant(member, category, fields);
    }

    /** 맛집 정보를 바꾼다. 작성자와 평점, 리뷰 수는 바꾸지 않는다. updated_at은 저장 시 갱신된다. */
    public void update(Category category, RestaurantFields fields) {
        this.category = category;
        apply(fields);
    }

    private void apply(RestaurantFields fields) {
        this.name = fields.name();
        this.address = fields.address();
        this.phone = fields.phone();
        this.description = fields.description();
        this.latitude = fields.latitude();
        this.longitude = fields.longitude();
    }

    public boolean isOwnedBy(long memberId) {
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
    public Member getMember() { return member; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getDescription() { return description; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAverageRating() { return averageRating; }
    public int getReviewCount() { return reviewCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** 저장할 맛집 입력값. 검증과 정규화(공백 제거, 빈 전화번호는 null)를 마친 값이다. */
    public record RestaurantFields(String name, String address, String phone, String description,
                                   BigDecimal latitude, BigDecimal longitude) {
    }
}
