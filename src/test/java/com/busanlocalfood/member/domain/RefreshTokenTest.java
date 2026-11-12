package com.busanlocalfood.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final String VALID_HASH =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    /** 기존 백필(MySQL UUID()) 형식의 UUID v1 예시. */
    private static final String V1_FAMILY = "c232ab00-9414-11ec-b3c8-9e6bdeccc4c0";

    /** 신규 생성 형식의 UUID v4 예시. */
    private static final String V4_FAMILY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private final Member member = Member.create("a@example.com", "$2a$10$hash", "부산먹보", null);

    @Test
    void 해시로_행을_만들면_해시와_만료_시각을_보관한다() {
        LocalDateTime expires = LocalDateTime.of(2026, 12, 1, 0, 0);

        RefreshToken token = RefreshToken.issueNewFamily(member, VALID_HASH, expires);

        assertThat(token.getTokenHash()).isEqualTo(VALID_HASH);
        assertThat(token.getExpiresAt()).isEqualTo(expires);
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getMember()).isSameAs(member);
    }

    @Test
    void 해시_형식이_아니면_거부한다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);

        // 원문 토큰처럼 보이는 값, 길이가 다른 값, 대문자 16진수는 모두 거부한다.
        assertThatThrownBy(() -> RefreshToken.issueNewFamily(member, "raw-refresh-token-value", expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueNewFamily(member, VALID_HASH.substring(1), expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueNewFamily(member, VALID_HASH.toUpperCase(), expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueNewFamily(member, null, expires))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 해시_검증은_기존_Family_경로에도_그대로_적용된다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);

        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, V4_FAMILY, "raw-refresh-token-value", expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, V4_FAMILY, null, expires))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 신규_Family_생성시_family_id를_새로_만든다() {
        RefreshToken token = RefreshToken.issueNewFamily(member, VALID_HASH, LocalDateTime.now().plusDays(14));

        assertThat(token.getFamilyId()).isNotNull();
        assertThat(UUID.fromString(token.getFamilyId())).isNotNull();
    }

    @Test
    void 신규_Family_ID는_UUID_v4이고_RFC4122_variant이다() {
        RefreshToken token = RefreshToken.issueNewFamily(member, VALID_HASH, LocalDateTime.now().plusDays(14));

        UUID familyId = UUID.fromString(token.getFamilyId());
        assertThat(familyId.version()).isEqualTo(4);
        assertThat(familyId.variant()).isEqualTo(2);
        // 소문자 canonical 형식이어야 ascii_bin 컬럼에서 같은 값으로 비교된다.
        assertThat(token.getFamilyId()).isEqualTo(token.getFamilyId().toLowerCase());
    }

    @Test
    void 연속으로_만든_신규_Family_ID는_서로_다르다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);

        String first = RefreshToken.issueNewFamily(member, VALID_HASH, expires).getFamilyId();
        String second = RefreshToken.issueNewFamily(member, VALID_HASH, expires).getFamilyId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 기존_Family_ID_v4를_전달하면_값이_그대로_유지된다() {
        RefreshToken rotated = RefreshToken.issueInFamily(
                member, V4_FAMILY, VALID_HASH, LocalDateTime.now().plusDays(14));

        assertThat(rotated.getFamilyId()).isEqualTo(V4_FAMILY);
    }

    @Test
    void 기존_Family_ID_v1을_전달하면_값이_그대로_유지된다() {
        RefreshToken backfilled = RefreshToken.issueInFamily(
                member, V1_FAMILY, VALID_HASH, LocalDateTime.now().plusDays(14));

        assertThat(backfilled.getFamilyId()).isEqualTo(V1_FAMILY);
        assertThat(UUID.fromString(backfilled.getFamilyId()).version()).isEqualTo(1);
    }

    @Test
    void 기존_Family_유지_경로는_회전_시_이전_family_id를_그대로_쓴다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);
        RefreshToken original = RefreshToken.issueNewFamily(member, VALID_HASH, expires);

        String nextHash = "b".repeat(64);
        RefreshToken rotated = RefreshToken.issueInFamily(member, original.getFamilyId(), nextHash, expires);

        assertThat(rotated.getFamilyId()).isEqualTo(original.getFamilyId());
        assertThat(rotated.getTokenHash()).isEqualTo(nextHash);
    }

    @Test
    void 잘못된_family_id는_거부한다() {
        LocalDateTime expires = LocalDateTime.now().plusDays(14);

        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, null, VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, "", VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, "not-a-uuid", VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        // 대문자 UUID는 ascii_bin에서 다른 값으로 취급되므로 거부한다.
        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, V4_FAMILY.toUpperCase(), VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        // UUID v5, nil UUID, 잘못된 variant, 하이픈 없는 값, 앞뒤 공백은 모두 거부한다.
        assertThatThrownBy(() -> RefreshToken.issueInFamily(
                member, "886313e1-3b8a-5372-9b90-0c9aee199e5d", VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(
                member, "00000000-0000-0000-0000-000000000000", VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(
                member, "c232ab00-9414-11ec-03c8-9e6bdeccc4c0", VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(
                member, V4_FAMILY.replace("-", ""), VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, " " + V4_FAMILY, VALID_HASH, expires))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_family_id_예외_메시지에는_입력값이_없다() {
        String bad = "secret-looking-family-value";

        assertThatThrownBy(() -> RefreshToken.issueInFamily(member, bad, VALID_HASH, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(bad));
    }

    @Test
    void toString에는_family_id와_token_hash가_나오지_않는다() {
        RefreshToken token = RefreshToken.issueInFamily(
                member, V4_FAMILY, VALID_HASH, LocalDateTime.now().plusDays(14));

        String text = token.toString();

        assertThat(text).doesNotContain(V4_FAMILY).doesNotContain(VALID_HASH);
        assertThat(text).startsWith("RefreshToken[");
    }
}
