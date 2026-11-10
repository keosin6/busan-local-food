package com.busanlocalfood.member.domain;

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
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh Token 행. 테이블 정의는 DATABASE_ERD 2.8을 따른다.
 * 원문은 이 Entity에 들어오지 않는다. SHA-256 해시(64자리 16진수)만 저장한다 (FR-01-05).
 * 발급·갱신·폐기 로직은 다음 단계에서 구현한다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    private static final int SHA256_HEX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = SHA256_HEX_LENGTH)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 폐기 시각. 폐기된 행은 재사용 탐지를 위해 삭제하지 않는다 (T7 A안). */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshToken() {
        // JPA 기본 생성자
    }

    private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 해시로 Refresh Token 행을 만든다. 원문 토큰을 받는 생성자는 없다.
     * tokenHash는 SHA-256 결과를 소문자 16진수 64자로 전달해야 한다.
     */
    public static RefreshToken issue(Member member, String tokenHash, LocalDateTime expiresAt) {
        Objects.requireNonNull(member, "member는 필수입니다.");
        Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
        if (tokenHash == null || tokenHash.length() != SHA256_HEX_LENGTH || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash는 SHA-256 16진수 64자여야 합니다.");
        }
        return new RefreshToken(member, tokenHash, expiresAt);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
