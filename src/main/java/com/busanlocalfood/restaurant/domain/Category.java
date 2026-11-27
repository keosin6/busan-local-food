package com.busanlocalfood.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 맛집 카테고리 (DATABASE_ERD 2.2). 시드 데이터로만 채운다. 추가·수정은 이번 범위 밖이다.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
        // JPA 기본 생성자
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getSortOrder() { return sortOrder; }
}
