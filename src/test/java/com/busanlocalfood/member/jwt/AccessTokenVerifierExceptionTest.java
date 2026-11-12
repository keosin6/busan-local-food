package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 검증 실패 시 외부로 나가는 예외가 원인 예외와 토큰 원문을 담지 않는지 확인한다.
 * 디코더는 Mockito로 대체하여 라이브러리가 던지는 예외를 직접 만든다.
 */
class AccessTokenVerifierExceptionTest {

    private static final String RAW_TOKEN = "eyJhbGciOiJIUzI1NiJ9.RAW-PAYLOAD-SECRET.RAW-SIGNATURE-XYZ";

    @Test
    void jwtExceptionIsConvertedWithoutCause() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenThrow(new JwtException("signature mismatch"));
        AccessTokenVerifier verifier = new AccessTokenVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(InvalidAccessTokenException.class)
                .satisfies(e -> assertThat(e.getCause()).isNull())
                .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
    }

    @Test
    void jwtExceptionKeepsFixedMessageAndHidesTokenValue() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        // 라이브러리 메시지에 토큰 조각이 섞여 들어오는 상황을 가정한다.
        when(decoder.decode(anyString())).thenThrow(new JwtException("bad jwt: " + RAW_TOKEN));
        AccessTokenVerifier verifier = new AccessTokenVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("token rejected")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(RAW_TOKEN))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("RAW-PAYLOAD-SECRET"));
    }

    @Test
    void illegalArgumentExceptionIsConvertedWithoutCause() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenThrow(new IllegalArgumentException("malformed: " + RAW_TOKEN));
        AccessTokenVerifier verifier = new AccessTokenVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("token rejected")
                .satisfies(e -> assertThat(e.getCause()).isNull())
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(RAW_TOKEN));
    }

    @Test
    void nonNumericSubjectIsConvertedWithoutCauseOrSubjectValue() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(RAW_TOKEN)
                .header("alg", "HS256")
                .subject("not-a-number")
                .claim(JwtSpec.CLAIM_ROLE, "USER")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenReturn(jwt);
        AccessTokenVerifier verifier = new AccessTokenVerifier(decoder);

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("subject format")
                .satisfies(e -> assertThat(e.getCause()).isNull())
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("not-a-number"));
    }

    @Test
    void missingTokenStillUsesFixedMessage() {
        AccessTokenVerifier verifier = new AccessTokenVerifier(mock(JwtDecoder.class));

        assertThatThrownBy(() -> verifier.verify(" "))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("token missing")
                .satisfies(e -> assertThat(e.getCause()).isNull());
    }
}
