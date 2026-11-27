package com.busanlocalfood.admin.service;

import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.security.AdminAccess;
import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.repository.StorageDeleteTaskRepository;
import com.busanlocalfood.image.service.StorageCleanup;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * S3 삭제 수동 재시도 (FUNCTION_SPEC F1-09, API_SPEC 11). 자동 스케줄러는 없다. 관리자가 요청할 때만 돈다.
 *
 * 대기 행을 id 순서로 배치 단위로 읽고, 행마다 기존 StorageCleanup.deleteAfterCommit을 부른다.
 * 성공하면 StorageCleanup이 추적 행을 지우고, 실패하면 retry_count와 last_error를 남긴다. 실패가 다음 행을 막지 않는다.
 * S3 DeleteObject는 없는 키에도 성공을 돌려주므로 이미 지워진 객체는 성공으로 처리된다.
 * 한 프로세스 안에서는 동시에 한 번만 돈다. 여러 서버 인스턴스 사이의 동시 실행은 막지 않는다(미검증 항목).
 */
@Service
public class AdminStorageRetryService {

    static final int BATCH_SIZE = 50;
    static final String UNEXPECTED_MESSAGE = "재시도 중 예상하지 못한 오류가 났습니다.";

    private final StorageDeleteTaskRepository tasks;
    private final StorageCleanup cleanup;
    private final ReentrantLock running = new ReentrantLock();

    public AdminStorageRetryService(StorageDeleteTaskRepository tasks, StorageCleanup cleanup) {
        this.tasks = tasks;
        this.cleanup = cleanup;
    }

    public RetryResult retry(Authentication actor) {
        AdminAccess.requireAdmin(actor);
        if (!running.tryLock()) {
            throw new BusinessException(ErrorCode.STORAGE_RETRY_RUNNING);
        }
        try {
            return runAll();
        } finally {
            running.unlock();
        }
    }

    private RetryResult runAll() {
        int processed = 0;
        int succeeded = 0;
        long cursor = 0L;
        while (true) {
            List<StorageDeleteTask> batch = tasks.findByIdGreaterThanOrderByIdAsc(cursor,
                    PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (StorageDeleteTask task : batch) {
                cursor = task.getId();
                processed++;
                if (retryOne(task.getS3Key())) {
                    succeeded++;
                }
            }
        }
        return new RetryResult(processed, succeeded, processed - succeeded);
    }

    /** 한 행을 재시도한다. 어떤 예외도 밖으로 내지 않는다. 성공하면 true다. */
    private boolean retryOne(String s3Key) {
        try {
            return cleanup.deleteAfterCommit(s3Key);
        } catch (RuntimeException e) {
            try {
                cleanup.recordFailureInNewTransaction(s3Key, UNEXPECTED_MESSAGE);
            } catch (RuntimeException ignored) {
                // 기록마저 실패하면 행은 그대로 남는다. 다음 재시도에서 다시 본다.
            }
            return false;
        }
    }
}
