package com.busanlocalfood.global.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 직후 DB 연결을 한 번 확인한다.
 * 연결에 실패하면 SQLException이 기동 오류가 되며, DatabaseConnectionFailureAnalyzer가 원인과 환경변수 안내를 출력한다.
 */
@Component
public class DatabaseConnectionCheck implements ApplicationRunner {

    private final DataSource dataSource;

    public DatabaseConnectionCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection ignored = dataSource.getConnection()) {
            // 연결을 얻을 수 있으면 충분하다.
        }
    }
}
