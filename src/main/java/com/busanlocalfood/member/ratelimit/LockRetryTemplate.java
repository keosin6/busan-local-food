package com.busanlocalfood.member.ratelimit;

import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 잠금 대기 시간 초과와 교착 상태에 대한 재시도 실행기.
 *
 * 규칙:
 * - 호출 시점에 활성 트랜잭션이 없으면 새 트랜잭션을 열고, 실패하면 전체를 롤백한 뒤 새 트랜잭션에서 처음부터 다시 실행한다.
 *   롤백된 시도의 이벤트와 상태 변경은 남지 않는다.
 * - 이미 외부 트랜잭션이 열려 있으면 재시도하지 않고 그대로 실행한다. 바깥에서 rollback-only가 된 트랜잭션을
 *   안에서 다시 실행하면 같은 실패가 반복되므로, 재시도는 트랜잭션 경계 바깥에서만 한다.
 * - MAX_ATTEMPTS번 모두 실패하면 RateLimitUnavailableException을 던진다. 요청은 허용되지 않는다 (fail-closed).
 */
public final class LockRetryTemplate {

    public static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactions;

    public LockRetryTemplate(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** work는 재시도될 수 있으므로 여러 번 실행해도 외부 상태를 중복으로 바꾸지 않아야 한다. 롤백으로 원상복구되는 DB 작업만 한다. */
    public <T> T execute(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return work.get();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> work.get());
            } catch (PessimisticLockingFailureException e) {
                // 잠금 대기 시간 초과와 교착 상태가 여기에 해당한다. 트랜잭션은 이미 롤백되었다.
                if (attempt >= MAX_ATTEMPTS) {
                    throw new RateLimitUnavailableException();
                }
            }
        }
    }
}
