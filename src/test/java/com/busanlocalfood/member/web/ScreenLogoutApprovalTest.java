package com.busanlocalfood.member.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/**
 * 화면 로그아웃 승인 속성의 단위 규칙: 1회 소비, 만료, 세션 분리, 동시 소비의 원자성.
 * 실행 순서는 Thread.sleep이 아니라 CountDownLatch로 결정적으로 맞춘다.
 */
class ScreenLogoutApprovalTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 200;

    @Test
    void 승인은_한_번만_소비할_수_있다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ScreenLogoutApproval.SESSION_KEY,
                new ScreenLogoutApproval(Instant.now().plus(ScreenLogoutApproval.TTL)));

        assertThat(ScreenLogoutApproval.consume(session)).isTrue();
        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
        assertThat(session.getAttribute(ScreenLogoutApproval.SESSION_KEY)).isNull();
    }

    @Test
    void 만료된_승인은_소비되지_않고_속성도_제거되며_재사용할_수_없다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ScreenLogoutApproval.SESSION_KEY, new ScreenLogoutApproval(Instant.now().minusSeconds(1)));

        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
        assertThat(session.getAttribute(ScreenLogoutApproval.SESSION_KEY)).isNull();
        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
    }

    @Test
    void 승인이_없으면_consume은_false다() {
        assertThat(ScreenLogoutApproval.consume(new MockHttpSession())).isFalse();
    }

    @Test
    void 이미_무효화된_세션에서_consume은_예외_없이_false다() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ScreenLogoutApproval.SESSION_KEY,
                new ScreenLogoutApproval(Instant.now().plus(ScreenLogoutApproval.TTL)));
        session.invalidate();

        assertThat(ScreenLogoutApproval.consume(session)).isFalse();
    }

    @Test
    void 승인의_유효_기간은_120초다() {
        assertThat(ScreenLogoutApproval.TTL.getSeconds()).isEqualTo(120);
    }

    @Test
    void 동일_승인에_대한_동시_consume은_매_라운드_정확히_한_번만_성공한다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                MockHttpSession session = new MockHttpSession();
                session.setAttribute(ScreenLogoutApproval.SESSION_KEY,
                        new ScreenLogoutApproval(Instant.now().plus(ScreenLogoutApproval.TTL)));

                List<Boolean> results = consumeConcurrently(pool, session, THREADS);

                assertThat(results).hasSize(THREADS);
                assertThat(results.stream().filter(Boolean::booleanValue).count())
                        .as("라운드 %d의 성공 횟수", round)
                        .isEqualTo(1);
                assertThat(session.getAttribute(ScreenLogoutApproval.SESSION_KEY)).isNull();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 만료된_승인에_대한_동시_consume은_모두_false이고_재사용되지_않는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(ScreenLogoutApproval.SESSION_KEY,
                    new ScreenLogoutApproval(Instant.now().minusSeconds(1)));

            List<Boolean> results = consumeConcurrently(pool, session, THREADS);

            assertThat(results).containsOnly(false);
            assertThat(ScreenLogoutApproval.consume(session)).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 모든 스레드를 시작 래치에서 대기시킨 뒤 동시에 출발시킨다. 결과를 모두 수집할 때까지 기다린다.
     * Thread.sleep 없이 래치만 쓴다.
     */
    private static List<Boolean> consumeConcurrently(ExecutorService pool, MockHttpSession session, int threads)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("시작 신호를 받지 못했다");
                }
                return ScreenLogoutApproval.consume(session);
            };
            futures.add(pool.submit(task));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        return results;
    }
}
