package com.busanlocalfood.member.ratelimit;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 만료 이벤트 정리 (R3). 생성 후 24시간이 지난 이벤트만 지운다. 한 번에 지우는 행 수를 batchSize로 제한한다.
 * 정리 실패는 호출자가 처리한다. 인증 요청의 성공이나 실패를 정리 결과로 바꾸지 않는다.
 */
@Component
public class AuthRateLimitCleanup {

    private final AuthRateLimitRepository repository;

    public AuthRateLimitCleanup(AuthRateLimitRepository repository) {
        this.repository = repository;
    }

    /** 한 테이블에서 한 배치만 지운다. */
    public int purgeOneBatch(EventTable table, LocalDateTime now, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다.");
        }
        return repository.purgeBatch(table, now.minus(AuthRateLimitPolicy.RETENTION), batchSize);
    }

    /** 모든 이벤트 테이블을 정리한다. 테이블마다 최대 maxBatchesPerTable번까지 반복해 한 번의 실행 시간을 제한한다. */
    public int purgeExpired(LocalDateTime now, int batchSize, int maxBatchesPerTable) {
        int total = 0;
        for (EventTable table : EventTable.values()) {
            for (int round = 0; round < maxBatchesPerTable; round++) {
                int deleted = purgeOneBatch(table, now, batchSize);
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
        }
        return total;
    }
}
