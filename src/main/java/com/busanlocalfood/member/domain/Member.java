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

    /**
     * 기존 회원에게 Google 계정을 연결한다 (API_SPEC 2.6.1). 호출자가 sub와 email_verified를 검증한다.
     * 이미 연결된 회원에게는 다른 sub를 연결할 수 없다. 같은 sub의 멱등 처리는 서비스가 한다.
     */
    public void attachGoogleSub(String googleSub) {
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException("google sub는 필수입니다.");
        }
        if (this.googleSub != null) {
            throw new IllegalStateException("이미 Google 계정이 연결되어 있습니다.");
        }
        this.googleSub = googleSub;
    }

    /**
     * Google 연결을 해제한다 (API_SPEC 2.7). 비밀번호 해시가 없으면 로그인 수단이 사라지므로 해제하지 않는다.
     */
    public void detachGoogleSub() {
        if (passwordHash == null) {
            throw new IllegalStateException("비밀번호가 없으면 Google 연결을 해제할 수 없습니다.");
        }
        this.googleSub = null;
    }

    /**
     * Google 전용 회원에게 로컬 비밀번호 해시를 설정한다 (API_SPEC 2.7.1). 이미 있으면 바꾸지 않는다. 변경은 이번 범위가 아니다.
     */
    public void assignPasswordHash(String hashed) {
        if (hashed == null || hashed.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시는 필수입니다.");
        }
        if (this.passwordHash != null) {
            throw new IllegalStateException("이미 비밀번호가 설정되어 있습니다.");
        }
        this.passwordHash = hashed;
    }

    public boolean hasPassword() { return passwordHash != null; }
    public boolean isGoogleLinked() { return googleSub != null; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public String getGoogleSub() { return googleSub; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
