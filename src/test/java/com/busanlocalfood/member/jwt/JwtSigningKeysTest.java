package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtSigningKeysTest {

    @Test
    void Base64_32바이트_키를_로딩한다() {
        String encoded = Base64.getEncoder().encodeToString(new byte[32]);

        SecretKey key = JwtSigningKeys.fromBase64(encoded);

        assertThat(key.getEncoded()).hasSize(32);
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void 긴_키도_허용한다() {
        String encoded = Base64.getEncoder().encodeToString(new byte[64]);

        assertThat(JwtSigningKeys.fromBase64(encoded).getEncoded()).hasSize(64);
    }

    @Test
    void 디코딩_후_32바이트_미만이면_시작을_실패시킨다() {
        String encoded = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> JwtSigningKeys.fromBase64(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트 이상");
    }

    @Test
    void 잘못된_Base64_문자열이면_실패시키고_입력값을_메시지에_넣지_않는다() {
        String bad = "@@not-base64@@!";

        assertThatThrownBy(() -> JwtSigningKeys.fromBase64(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(bad))
                .satisfies(e -> assertThat(e.getCause()).isNull());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 키가_없으면_실패시킨다(String encoded) {
        assertThatThrownBy(() -> JwtSigningKeys.fromBase64(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
