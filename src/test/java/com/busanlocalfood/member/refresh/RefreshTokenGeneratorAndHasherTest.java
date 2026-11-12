package com.busanlocalfood.member.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorAndHasherTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void 원문은_256비트_이상이고_URL_safe_Base64_패딩없음_형식이다() {
        String token = generator.generate();

        // 32바이트를 패딩 없는 URL-safe Base64로 인코딩하면 43자다.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(token).doesNotContain("=", "+", "/");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void 연속_생성한_원문은_서로_중복되지_않는다() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(1_000);
    }

    @Test
    void SHA_256_해시는_소문자_16진수_64자이고_알려진_값과_일치한다() {
        // SHA-256("abc") 표준 테스트 벡터
        String hash = RefreshTokenHasher.sha256Hex("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void 같은_원문은_같은_해시를_내고_원문과_해시는_다르다() {
        String token = generator.generate();

        assertThat(RefreshTokenHasher.sha256Hex(token)).isEqualTo(RefreshTokenHasher.sha256Hex(token));
        assertThat(RefreshTokenHasher.sha256Hex(token)).isNotEqualTo(token);
    }

    @Test
    void 빈_원문은_해시하지_않고_거부한다() {
        assertThatThrownBy(() -> RefreshTokenHasher.sha256Hex(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshTokenHasher.sha256Hex(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 발급_결과의_toString에는_원문이_나오지_않는다() {
        String token = generator.generate();
        IssuedRefreshToken issued = new IssuedRefreshToken(token, java.time.LocalDateTime.now());

        assertThat(issued.toString()).doesNotContain(token).contains("[REDACTED]");
        assertThat(issued.token()).isEqualTo(token);
    }

    @Test
    void 만료_초가_0_이하이면_설정을_거부한다() {
        assertThatThrownBy(() -> new RefreshTokenProperties(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new RefreshTokenProperties(1209600).refreshTokenExpirySeconds()).isEqualTo(1209600);
    }
}
