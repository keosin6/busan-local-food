package com.busanlocalfood.member.ratelimit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 요청 제한 이벤트와 카운터의 SQL 접근 (SQL 003).
 * 모든 식별자 값은 바인딩 파라미터로만 전달한다. 테이블과 컬럼 이름은 EventTable 상수만 쓴다.
 * 카운터 행은 횟수를 저장하지 않는 잠금 행이다. 이 클래스는 자동 삭제를 하지 않는다.
 */
@Repository
public class AuthRateLimitRepository {

    private final JdbcTemplate jdbc;

    public AuthRateLimitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 카운터 행을 확보하고 잠근다. 키는 사전순으로 정렬된 목록이어야 한다 (교착 상태 방지).
     * INSERT ... ON DUPLICATE KEY UPDATE로 행을 만든 뒤 SELECT ... FOR UPDATE로 잠근다.
     */
    public void lockCounters(List<String> sortedCounterKeys) {
        for (String key : sortedCounterKeys) {
            jdbc.update("INSERT INTO auth_rate_limit_counter (counter_key) VALUES (?) "
                    + "ON DUPLICATE KEY UPDATE counter_key = counter_key", key);
        }
        for (String key : sortedCounterKeys) {
            jdbc.queryForObject("SELECT counter_key FROM auth_rate_limit_counter WHERE counter_key = ? FOR UPDATE",
                    String.class, key);
        }
    }

    public List<LocalDateTime> recentLoginFailuresByPair(String pairKeyHash, LocalDateTime since, int limit) {
        return jdbc.queryForList(
                "SELECT failed_at FROM login_failure_event WHERE pair_key_hash = ? AND failed_at > ? "
                        + "ORDER BY failed_at DESC LIMIT ?",
                LocalDateTime.class, pairKeyHash, since, limit);
    }

    public List<LocalDateTime> recentLoginFailuresByIp(String ipKeyHash, LocalDateTime since, int limit) {
        return jdbc.queryForList(
                "SELECT failed_at FROM login_failure_event WHERE ip_key = ? AND failed_at > ? "
                        + "ORDER BY failed_at DESC LIMIT ?",
                LocalDateTime.class, ipKeyHash, since, limit);
    }

    public void insertLoginFailure(String pairKeyHash, String ipKeyHash, LocalDateTime at) {
        jdbc.update("INSERT INTO login_failure_event (pair_key_hash, ip_key, failed_at) VALUES (?, ?, ?)",
                pairKeyHash, ipKeyHash, at);
    }

    /** 성공 로그인 시 이메일+IP 조합의 실패 이벤트만 지운다. 같은 IP의 다른 이메일 기록은 남긴다. */
    public void deleteLoginFailuresByPair(String pairKeyHash) {
        jdbc.update("DELETE FROM login_failure_event WHERE pair_key_hash = ?", pairKeyHash);
    }

    public List<LocalDateTime> recentSignupRequests(String ipKeyHash, LocalDateTime since, int limit) {
        return jdbc.queryForList(
                "SELECT requested_at FROM signup_request_event WHERE ip_key = ? AND requested_at > ? "
                        + "ORDER BY requested_at DESC LIMIT ?",
                LocalDateTime.class, ipKeyHash, since, limit);
    }

    public void insertSignupRequest(String ipKeyHash, LocalDateTime at) {
        jdbc.update("INSERT INTO signup_request_event (ip_key, requested_at) VALUES (?, ?)", ipKeyHash, at);
    }

    public List<LocalDateTime> recentGoogleOAuthRequests(String ipKeyHash, LocalDateTime since, int limit) {
        return jdbc.queryForList(
                "SELECT requested_at FROM google_oauth_request_event WHERE ip_key = ? AND requested_at > ? "
                        + "ORDER BY requested_at DESC LIMIT ?",
                LocalDateTime.class, ipKeyHash, since, limit);
    }

    public void insertGoogleOAuthRequest(String ipKeyHash, LocalDateTime at) {
        jdbc.update("INSERT INTO google_oauth_request_event (ip_key, requested_at) VALUES (?, ?)", ipKeyHash, at);
    }

    public List<LocalDateTime> recentRefreshRequests(String familyId, LocalDateTime since, int limit) {
        return jdbc.queryForList(
                "SELECT requested_at FROM refresh_request_event WHERE family_id = ? AND requested_at > ? "
                        + "ORDER BY requested_at DESC LIMIT ?",
                LocalDateTime.class, familyId, since, limit);
    }

    public void insertRefreshRequest(String familyId, LocalDateTime at) {
        jdbc.update("INSERT INTO refresh_request_event (family_id, requested_at) VALUES (?, ?)", familyId, at);
    }

    /** cutoff보다 오래된 이벤트를 최대 batchSize건 지운다. 지운 행 수를 돌려준다. 반복 실행해도 안전하다. */
    public int purgeBatch(EventTable table, LocalDateTime cutoff, int batchSize) {
        return jdbc.update("DELETE FROM " + table.tableName() + " WHERE " + table.timeColumn() + " < ? LIMIT ?",
                cutoff, batchSize);
    }
}
