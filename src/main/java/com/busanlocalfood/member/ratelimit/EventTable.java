package com.busanlocalfood.member.ratelimit;

/**
 * 요청 제한 이벤트 테이블 (SQL 003). 테이블과 컬럼 이름은 이 enum의 상수만 쓴다. 외부 입력을 SQL 식별자로 쓰지 않는다.
 */
public enum EventTable {
    LOGIN_FAILURE("login_failure_event", "failed_at"),
    SIGNUP_REQUEST("signup_request_event", "requested_at"),
    REFRESH_REQUEST("refresh_request_event", "requested_at"),
    GOOGLE_OAUTH_REQUEST("google_oauth_request_event", "requested_at");

    private final String tableName;
    private final String timeColumn;

    EventTable(String tableName, String timeColumn) {
        this.tableName = tableName;
        this.timeColumn = timeColumn;
    }

    public String tableName() {
        return tableName;
    }

    public String timeColumn() {
        return timeColumn;
    }
}
