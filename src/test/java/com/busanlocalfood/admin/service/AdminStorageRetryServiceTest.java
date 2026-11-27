package com.busanlocalfood.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.domain.StorageDeleteTask;
import com.busanlocalfood.image.repository.StorageDeleteTaskRepository;
import com.busanlocalfood.image.service.StorageCleanup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * S3 삭제 수동 재시도 (F1-09). 저장소와 S3 협력자는 목이다. 실제 S3를 호출하지 않는다.
 * 확인: 여러 배치 처리, 한 건 실패가 나머지를 막지 않음, 예상 밖 예외의 기록, 권한 없음, 동시 실행 막기.
 */
class AdminStorageRetryServiceTest {

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(9L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static Authentication user() {
        return new UsernamePasswordAuthenticationToken(7L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static StorageDeleteTask task(long id, String key) {
        StorageDeleteTask task = StorageDeleteTask.create(key);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    @Test
    void 대기_행을_모두_처리하고_성공과_실패를_센다() {
        StorageDeleteTaskRepository tasks = mock(StorageDeleteTaskRepository.class);
        StorageCleanup cleanup = mock(StorageCleanup.class);
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(0L), any()))
                .thenReturn(List.of(task(1, "a"), task(2, "b")));
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(2L), any()))
                .thenReturn(List.of(task(3, "c")));
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(3L), any())).thenReturn(List.of());
        when(cleanup.deleteAfterCommit("a")).thenReturn(true);
        when(cleanup.deleteAfterCommit("b")).thenReturn(false);
        when(cleanup.deleteAfterCommit("c")).thenReturn(true);

        RetryResult result = new AdminStorageRetryService(tasks, cleanup).retry(admin());

        assertThat(result).isEqualTo(new RetryResult(3, 2, 1));
    }

    @Test
    void 한_건이_예외로_실패해도_나머지_행을_계속_처리한다() {
        StorageDeleteTaskRepository tasks = mock(StorageDeleteTaskRepository.class);
        StorageCleanup cleanup = mock(StorageCleanup.class);
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(0L), any()))
                .thenReturn(List.of(task(1, "boom"), task(2, "ok")));
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(2L), any())).thenReturn(List.of());
        when(cleanup.deleteAfterCommit("boom")).thenThrow(new IllegalStateException("예상 밖"));
        when(cleanup.deleteAfterCommit("ok")).thenReturn(true);

        RetryResult result = new AdminStorageRetryService(tasks, cleanup).retry(admin());

        assertThat(result).isEqualTo(new RetryResult(2, 1, 1));
        verify(cleanup).recordFailureInNewTransaction(eq("boom"), anyString());
    }

    @Test
    void 대기_행이_없으면_0건이다() {
        StorageDeleteTaskRepository tasks = mock(StorageDeleteTaskRepository.class);
        when(tasks.findByIdGreaterThanOrderByIdAsc(anyLong(), any())).thenReturn(List.of());

        assertThat(new AdminStorageRetryService(tasks, mock(StorageCleanup.class)).retry(admin()))
                .isEqualTo(new RetryResult(0, 0, 0));
    }

    @Test
    void 회원은_재시도할_수_없고_저장소를_건드리지_않는다() {
        StorageDeleteTaskRepository tasks = mock(StorageDeleteTaskRepository.class);
        StorageCleanup cleanup = mock(StorageCleanup.class);

        assertThatThrownBy(() -> new AdminStorageRetryService(tasks, cleanup).retry(user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(tasks, cleanup);
    }

    @Test
    void 이미_실행_중이면_두_번째_요청은_STORAGE_RETRY_RUNNING이다() throws Exception {
        StorageDeleteTaskRepository tasks = mock(StorageDeleteTaskRepository.class);
        StorageCleanup cleanup = mock(StorageCleanup.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(0L), any())).thenReturn(List.of(task(1, "slow")));
        when(tasks.findByIdGreaterThanOrderByIdAsc(eq(1L), any())).thenReturn(List.of());
        when(cleanup.deleteAfterCommit("slow")).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return true;
        });
        AdminStorageRetryService service = new AdminStorageRetryService(tasks, cleanup);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RetryResult> first = pool.submit(() -> service.retry(admin()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.retry(admin()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_RETRY_RUNNING));

            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(new RetryResult(1, 1, 0));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
