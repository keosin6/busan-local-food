package com.busanlocalfood.global.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 시간대 기준. 애플리케이션, JDBC 변환, DB 세션이 모두 Asia/Seoul을 쓴다.
 * - JVM 기본 시간대: 기동 시 applyDefaultTimeZone()으로 고정한다 (LocalDateTime.now()와 JDBC 변환의 기준).
 * - DB 세션 시간대: application.yml의 hikari connection-init-sql로 +09:00에 고정한다 (NOW(), CURRENT_TIMESTAMP의 기준).
 * 셋이 다르면 앱이 저장한 시각과 DB가 만든 시각이 서로 어긋난다.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** JVM 기본 시간대를 서비스 시간대로 고정한다. DataSource가 만들어지기 전에 호출해야 한다. */
    public static void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_ZONE));
    }

    /** 애플리케이션 공용 시계. 시각이 필요한 빈은 이 빈을 주입받는다. 별도 Clock 빈을 추가하지 않는다. */
    @Bean
    public Clock serviceClock() {
        return Clock.system(SERVICE_ZONE);
    }
}
