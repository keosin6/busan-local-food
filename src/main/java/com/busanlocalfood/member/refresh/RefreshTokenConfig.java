package com.busanlocalfood.member.refresh;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Refresh Token 생명주기 설정 바인딩. 시계는 global.config.TimeConfig의 공용 Clock 빈을 쓴다. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {
}
