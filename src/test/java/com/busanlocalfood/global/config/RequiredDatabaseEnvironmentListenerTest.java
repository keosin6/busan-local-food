package com.busanlocalfood.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredDatabaseEnvironmentListenerTest {

    private final RequiredDatabaseEnvironmentListener listener = new RequiredDatabaseEnvironmentListener();

    @Test
    void reportsMissingPasswordWithoutLeakingPlaceholderAsValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/busan_local_food")
                .withProperty("spring.datasource.username", "busan")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertThatThrownBy(() -> listener.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageNotContaining("${DB_PASSWORD}");
    }

    @Test
    void reportsEveryMissingVariable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertThatThrownBy(() -> listener.validate(environment))
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void acceptsResolvedSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db.internal:3306/busan_local_food")
                .withProperty("spring.datasource.username", "busan")
                .withProperty("spring.datasource.password", "not-a-real-password");

        assertThatCode(() -> listener.validate(environment)).doesNotThrowAnyException();
    }
}
