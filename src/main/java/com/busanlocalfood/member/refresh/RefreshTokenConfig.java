package com.busanlocalfood.member.refresh;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Refresh Token 생명주기 빈 구성. 시계는 global.config.TimeConfig의 공용 Clock 빈을 쓴다. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

    /**
     * 요청 제한 서비스(R4)가 아직 없으므로 모든 요청을 통과시키는 기본 구현이다.
     * 요청 제한 서비스를 구현하면 RefreshRotationGate 빈을 등록하여 이 기본값을 대체한다.
     */
    @Bean
    @ConditionalOnMissingBean(RefreshRotationGate.class)
    RefreshRotationGate permitAllRefreshRotationGate() {
        return familyId -> true;
    }
}
