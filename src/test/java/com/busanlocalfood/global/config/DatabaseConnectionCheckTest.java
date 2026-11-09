package com.busanlocalfood.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseConnectionCheckTest {

    @Test
    void succeedsWhenConnectionCanBeObtained() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));

        assertThatCode(() -> new DatabaseConnectionCheck(dataSource).run(null)).doesNotThrowAnyException();
    }

    @Test
    void propagatesSqlExceptionWhenConnectionFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Access denied"));

        assertThatThrownBy(() -> new DatabaseConnectionCheck(dataSource).run(null))
                .isInstanceOf(SQLException.class)
                .hasMessage("Access denied");
    }
}
