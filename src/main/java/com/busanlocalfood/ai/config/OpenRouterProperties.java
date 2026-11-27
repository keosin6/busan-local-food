package com.busanlocalfood.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouter 설정 (application.yml의 app.ai.openrouter). 키와 모델 ID는 환경 변수로만 받는다 (NFR-18, DEVELOPMENT_RULES 5.4).
 * toString은 키를 가린다. 로그에 설정 객체를 찍어도 키가 나오지 않는다.
 */
@ConfigurationProperties(prefix = "app.ai.openrouter")
public record OpenRouterProperties(String baseUrl, String apiKey, String model, Integer timeoutSeconds) {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /** 키와 모델 ID와 기본 URL이 모두 있어야 외부 호출을 한다. 하나라도 없으면 요약은 UNAVAILABLE이다. */
    public boolean configured() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds);
    }

    @Override
    public String toString() {
        return "OpenRouterProperties[baseUrl=" + baseUrl + ", apiKey=" + (notBlank(apiKey) ? "****" : "(없음)")
                + ", model=" + (notBlank(model) ? "(설정됨)" : "(없음)") + ", timeoutSeconds=" + timeout().toSeconds() + "]";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
