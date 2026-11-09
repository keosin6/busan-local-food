package com.busanlocalfood.global.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * DB 접속 환경변수가 설정되지 않았으면 기동을 중단하고 누락된 변수 이름을 알려 준다.
 * 설정되지 않은 ${...} 플레이스홀더가 그대로 접속 값으로 쓰이는 것을 막는다.
 * META-INF/spring.factories에 등록되어 있다.
 */
public class RequiredDatabaseEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final List<RequiredSetting> REQUIRED_SETTINGS = List.of(
            new RequiredSetting("spring.datasource.url", "DB_URL"),
            new RequiredSetting("spring.datasource.username", "DB_USERNAME"),
            new RequiredSetting("spring.datasource.password", "DB_PASSWORD"));

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        validate(event.getEnvironment());
    }

    void validate(ConfigurableEnvironment environment) {
        List<String> missing = new ArrayList<>();
        for (RequiredSetting setting : REQUIRED_SETTINGS) {
            if (!isResolved(environment, setting.property())) {
                missing.add(setting.variable());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "필수 환경변수가 설정되지 않았습니다: " + String.join(", ", missing)
                            + ". DB 접속 정보는 환경변수로 주입해야 합니다. 개발 환경에서는 DB_PASSWORD만 지정하면 됩니다.");
        }
    }

    private boolean isResolved(ConfigurableEnvironment environment, String property) {
        try {
            String value = environment.getProperty(property);
            return value != null && !value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return false;
        }
    }

    private record RequiredSetting(String property, String variable) {
    }
}
