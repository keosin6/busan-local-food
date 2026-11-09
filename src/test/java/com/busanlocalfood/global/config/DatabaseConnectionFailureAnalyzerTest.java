package com.busanlocalfood.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

class DatabaseConnectionFailureAnalyzerTest {

    @Test
    void explainsRequiredEnvironmentVariablesWhenDatabaseConnectionFails() {
        FailureAnalysis analysis = new DatabaseConnectionFailureAnalyzer()
                .analyze(new SQLException("Communications link failure"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Communications link failure");
        assertThat(analysis.getAction()).contains("DB_URL", "DB_USERNAME", "DB_PASSWORD");
    }
}
