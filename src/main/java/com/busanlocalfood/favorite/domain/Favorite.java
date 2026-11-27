package com.busanlocalfood.favorite.domain;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 찜 Entity (DATABASE_ERD 2.6, FUNCTION_SPEC F1-06). 한 회원은 한 맛집을 한 번만 찜한다 (uk_favorite_member_restaurant).
 * 맛집이나 회원이 지워지면 DB의 CASCADE로 함께 지워진다.
 */
@Entity
@Table(name = "favorite")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Favorite() {
        // JPA 기본 생성자
    }

    private Favorite(Member member, Restaurant restaurant) {
        this.member = member;
        this.restaurant = restaurant;
    }

    public static Favorite create(Member member, Restaurant restaurant) {
        return new Favorite(member, restaurant);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public Restaurant getRestaurant() { return restaurant; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
