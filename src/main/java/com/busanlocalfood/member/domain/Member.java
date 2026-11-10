package com.busanlocalfood.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * 회원 Entity. 테이블 정의는 DATABASE_ERD 2.1을 따른다.
 * 유일성(email, nickname, google_sub)은 DB UNIQUE 제약이 최종 보장한다. 애플리케이션 검사는 사전 안내용이다.
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 로그인 ID. 소문자로 정규화하여 저장한다. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** BCrypt 해시. Google 전용 회원은 NULL이다. 원문은 저장하지 않는다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** 연동된 Google 계정의 sub. 미연동 회원은 NULL이다. */
    @Column(name = "google_sub", length = 255)
    private String googleSub;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Member() {
        // JPA 기본 생성자
    }

    private Member(String email, String passwordHash, String nickname, String googleSub) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.USER;
        this.googleSub = googleSub;
    }

    /**
     * 신규 회원을 만든다. 역할은 항상 USER이다.
     * 로그인 수단(비밀번호 해시 또는 Google sub) 중 최소 하나가 있어야 한다 (FR-01-13, DB CHECK와 같은 조건).
     */
    public static Member create(String email, String passwordHash, String nickname, String googleSub) {
        Objects.requireNonNull(email, "email은 필수입니다.");
        Objects.requireNonNull(nickname, "nickname은 필수입니다.");
        boolean hasPassword = passwordHash != null && !passwordHash.isBlank();
        boolean hasGoogle = googleSub != null && !googleSub.isBlank();
        if (!hasPassword && !hasGoogle) {
            throw new IllegalArgumentException("비밀번호 해시 또는 Google sub 중 하나는 필요합니다.");
        }
        return new Member(
                normalizeEmail(email),
                hasPassword ? passwordHash : null,
                nickname,
                hasGoogle ? googleSub : null);
    }

    /** 이메일 정규화 규칙. 저장과 조회에 같은 규칙을 사용한다 (Locale.ROOT 소문자, 앞뒤 공백 제거). */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public String getGoogleSub() { return googleSub; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
