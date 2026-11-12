package com.busanlocalfood.member.ratelimit;

import java.security.MessageDigest;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 요청 제한 빈 구성. HMAC 키가 없거나 잘못되면 기동을 막는다.
 * JWT 서명 키와 같은 값이면 기동을 막는다. 두 용도의 키를 섞지 않는다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({AuthRateLimitProperties.class, TrustedProxyProperties.class})
public class AuthRateLimitConfig {

    @Bean
    AuthRateLimitHasher authRateLimitHasher(AuthRateLimitProperties properties,
                                            ObjectProvider<SecretKey> jwtSigningKey) {
        SecretKey hmacKey = AuthRateLimitHasher.keyFromBase64(properties.hmacSecret());
        SecretKey jwtKey = jwtSigningKey.getIfAvailable();
        if (jwtKey != null && MessageDigest.isEqual(jwtKey.getEncoded(), hmacKey.getEncoded())) {
            throw new IllegalStateException("AUTH_RATE_LIMIT_HMAC_SECRET은 JWT_SECRET과 다른 값이어야 합니다.");
        }
        return new AuthRateLimitHasher(hmacKey);
    }

    @Bean
    LockRetryTemplate lockRetryTemplate(org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new LockRetryTemplate(transactionManager);
    }

    @Bean
    ClientIpResolver clientIpResolver(TrustedProxyProperties properties) {
        return new ClientIpResolver(properties.trustedProxies());
    }
}
