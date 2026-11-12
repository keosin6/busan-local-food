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
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh Token 행. 테이블 정의는 DATABASE_ERD 2.8을 따른다.
 * 원문은 이 Entity에 들어오지 않는다. SHA-256 해시(64자리 16진수)만 저장한다 (FR-01-05).
 * family_id는 토큰 계열 식별자(가명 식별자)다. 외부 응답, 로그, toString()에 노출하지 않는다.
 * 발급·갱신·폐기 서비스는 다음 단계에서 구현한다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    private static final int SHA256_HEX_LENGTH = 64;
    private static final int UUID_LENGTH = 36;

    /**
     * 허용하는 family_id 형식: 소문자 canonical 문자열이며 버전 1(기존 백필) 또는 4(신규 생성)이고 RFC 4122 variant다.
     * DB 컬럼은 ascii_bin이라 대소문자를 구분하므로, 대문자 입력은 거부해 같은 계열이 둘로 갈라지지 않게 한다.
     */
    private static final Pattern FAMILY_ID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[14][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

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

    /**
     * 토큰 계열 ID. DB 컬럼은 CHAR(36) ascii_bin이며 기본값이 없으므로 INSERT 때 반드시 값을 넣는다.
     * 회전 시 같은 값을 유지하고, 새 로그인 세션에서만 새로 만든다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", nullable = false, length = UUID_LENGTH)
    private String familyId;

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

    private RefreshToken(Member member, String familyId, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 새 로그인으로 새 Family를 시작하는 행을 만든다. family_id는 UUID v4로 새로 생성한다.
     * 원문 토큰을 받는 생성자는 없다. tokenHash는 SHA-256 결과를 소문자 16진수 64자로 전달해야 한다.
     */
    public static RefreshToken issueNewFamily(Member member, String tokenHash, LocalDateTime expiresAt) {
        return create(member, UUID.randomUUID().toString(), tokenHash, expiresAt);
    }

    /**
     * 기존 Family에 속하는 행을 만든다. 회전할 때 이전 행의 family_id를 그대로 전달한다.
     * familyId는 소문자 UUID v1(기존 백필) 또는 v4(신규 생성) 형식이어야 하며, 그 외 값은 거부한다.
     */
    public static RefreshToken issueInFamily(Member member, String familyId, String tokenHash,
                                             LocalDateTime expiresAt) {
        // 잘못된 값을 예외 메시지에 그대로 넣지 않는다. 계열 ID는 가명 식별자다.
        if (familyId == null || !FAMILY_ID_PATTERN.matcher(familyId).matches()) {
            throw new IllegalArgumentException("familyId는 소문자 UUID v1 또는 v4 형식이어야 합니다.");
        }
        return create(member, familyId, tokenHash, expiresAt);
    }

    private static RefreshToken create(Member member, String familyId, String tokenHash, LocalDateTime expiresAt) {
        Objects.requireNonNull(member, "member는 필수입니다.");
        Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
        if (tokenHash == null || tokenHash.length() != SHA256_HEX_LENGTH || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash는 SHA-256 16진수 64자여야 합니다.");
        }
        return new RefreshToken(member, familyId, tokenHash, expiresAt);
    }

    /**
     * 폐기한다. 행은 삭제하지 않는다 (재사용 탐지를 위해 보존, T7 A안).
     * 이미 폐기된 행은 처음 폐기된 시각을 유지한다. 중복 호출은 안전하다.
     */
    public void revoke(LocalDateTime at) {
        Objects.requireNonNull(at, "at은 필수입니다.");
        if (this.revokedAt == null) {
            this.revokedAt = at;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 만료 시각이 기준 시각 이전이거나 같으면 만료된 것으로 본다. */
    public boolean isExpiredAt(LocalDateTime now) {
        Objects.requireNonNull(now, "now는 필수입니다.");
        return !expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTokenHash() { return tokenHash; }
    public String getFamilyId() { return familyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** family_id와 token_hash는 출력하지 않는다. member는 지연 로딩이므로 참조하지 않는다. */
    @Override
    public String toString() {
        return "RefreshToken[id=" + id
                + ", expiresAt=" + expiresAt
                + ", revoked=" + (revokedAt != null) + "]";
    }
}
