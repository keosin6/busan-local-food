package com.busanlocalfood.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void BCrypt_인코더를_제공한다() {
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void 인코딩_결과는_원문과_다르고_matches로만_검증된다() {
        String raw = "correct-horse-battery";

        String encoded = encoder.encode(raw);

        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoded).startsWith("$2");
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void 같은_원문도_매번_다른_해시가_된다() {
        String raw = "same-input";

        assertThat(encoder.encode(raw)).isNotEqualTo(encoder.encode(raw));
    }
}
