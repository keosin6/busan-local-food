package com.busanlocalfood.image.service;

import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.repository.StorageDeleteTaskRepository;
import com.busanlocalfood.image.service.ImageStorage.StorageException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S3 객체 삭제 협력자. DB 트랜잭션 안에서는 키를 기록만 하고, 커밋 뒤에 S3를 지운다.
 *
 * - recordPending: 호출자의 DB 트랜잭션에 참여한다. 이 메서드가 롤백되면 기록도 함께 사라진다.
 * - deleteAfterCommit: 커밋 뒤에 호출한다. 성공하면 기록을 지우고, 실패하면 기록을 남긴다. 실패는 호출자에게 false로 알린다.
 * - recordFailureInNewTransaction: 보상 삭제가 실패했을 때 별도 트랜잭션으로 기록한다.
 */
@Component
public class StorageCleanup {

    private final StorageDeleteTaskRepository tasks;
    private final ImageStorage storage;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    public StorageCleanup(StorageDeleteTaskRepository tasks, ImageStorage storage,
                          PlatformTransactionManager transactionManager) {
        this.tasks = tasks;
        this.storage = storage;
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 호출자의 DB 트랜잭션 안에서 부른다. */
    public void recordPending(String s3Key) {
        if (tasks.findByS3Key(s3Key).isEmpty()) {
            tasks.save(StorageDeleteTask.create(s3Key));
        }
    }

    /**
     * DB 커밋 뒤에 부른다. 성공하면 true, 실패하면 false다.
     * 실패한 키는 storage_delete_task에 남는다. 이 메서드는 맛집이나 이미지 행을 되돌리지 않는다.
     */
    public boolean deleteAfterCommit(String s3Key) {
        try {
            storage.delete(s3Key);
        } catch (StorageException e) {
            recordFailure(s3Key, e.safeMessage());
            return false;
        }
        required.executeWithoutResult(status ->
                tasks.findByS3Key(s3Key).ifPresent(tasks::delete));
        return true;
    }

    /** 보상 삭제가 실패했을 때 별도 트랜잭션으로 기록한다. 롤백된 DB 작업과 독립적으로 남아야 한다. */
    public void recordFailureInNewTransaction(String s3Key, String safeMessage) {
        recordFailure(s3Key, safeMessage);
    }

    private void recordFailure(String s3Key, String safeMessage) {
        requiresNew.executeWithoutResult(status -> {
            StorageDeleteTask task = tasks.findByS3Key(s3Key).orElseGet(() -> StorageDeleteTask.create(s3Key));
            task.recordFailure(safeMessage);
            tasks.save(task);
        });
    }
}
