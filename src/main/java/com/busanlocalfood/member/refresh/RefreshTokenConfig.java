package com.busanlocalfood.member.refresh;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Refresh Token 생명주기 빈 구성. 만료 계산과 폐기 시각에 쓰는 Clock을 제공한다. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

    @Bean
    Clock refreshTokenClock() {
        return Clock.systemDefaultZone();
    }
}
