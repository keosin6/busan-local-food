package com.busanlocalfood.member.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 발급·검증 빈 구성. 서명 키가 잘못되면 여기서 기동이 실패한다.
 * 보안 설정(SecurityFilterChain)과 인증 필터 연결은 이 클래스의 책임이 아니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        return JwtSigningKeys.fromBase64(properties.secret());
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock clock) {
        // macAlgorithm(HS256)으로 알고리즘을 고정하므로 다른 알고리즘 토큰은 거부된다.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.accessTokenValidator(clock));
        return decoder;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        return new AccessTokenIssuer(jwtEncoder,
                java.time.Duration.ofSeconds(properties.accessTokenExpirySeconds()), clock);
    }

    @Bean
    AccessTokenVerifier accessTokenVerifier(JwtDecoder jwtDecoder) {
        return new AccessTokenVerifier(jwtDecoder);
    }
}
