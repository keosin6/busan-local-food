package com.busanlocalfood.global.config;

import java.sql.SQLException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 데이터베이스 연결 또는 초기화 실패 시 원인과 확인해야 할 환경변수를 안내한다.
 * META-INF/spring.factories에 등록되어 있다.
 */
public class DatabaseConnectionFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
        String description = "데이터베이스 연결 또는 초기화에 실패했습니다. 원인: " + cause.getMessage();
        String action = """
                다음 환경변수가 설정되어 있고 올바른지 확인하세요.
                  DB_URL       예: jdbc:mysql://localhost:3306/busan_local_food
                  DB_USERNAME  MySQL 사용자 이름
                  DB_PASSWORD  MySQL 비밀번호 (코드나 Git에 기록하지 마세요)
                MySQL 8 서버가 실행 중인지, 해당 사용자가 데이터베이스에 접근할 수 있는지도 확인하세요.
                """;
        return new FailureAnalysis(description, action, cause);
    }
}
