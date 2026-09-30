package com.example.demo.auth.application;

import com.example.demo.shared.context.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class ExecutionContextPropagationTest {
    private ExecutionContext context(long id) {
        return ExecutionContext.start(new UserContext(id), "test", ExecutionContext.Actor.SYSTEM,
                ExecutionPolicy.readOnly());
    }

    @Test
    void reusesThreadWithoutLeakingAfterSuccessFailureAndCancellation() throws Exception {
        ExecutorService executor = new ContextPropagatingExecutorService(Executors.newSingleThreadExecutor());
        try {
            var a = context(1);
            var b = context(2);
            try (var ignored = ExecutionContextScope.open(a)) {
                assertThat(executor.submit(
                        () -> ExecutionContextScope.requireCurrent().user().userId()).get()).isEqualTo(1L);
                assertThatThrownBy(() -> executor.submit(
                        () -> { throw new IllegalArgumentException("failed task"); }).get())
                        .isInstanceOf(ExecutionException.class);
            }
            CountDownLatch started = new CountDownLatch(1);
            Future<?> cancelled;
            try (var ignored = ExecutionContextScope.open(a)) {
                cancelled = executor.submit(() -> {
                    started.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                });
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> cancelled.get(10, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            cancelled.cancel(true);
            executor.submit(() -> {
                assertThatThrownBy(ExecutionContextScope::requireCurrent).isInstanceOf(IllegalStateException.class);
                assertThat(MDC.get("userId")).isNull();
            }).get(5, TimeUnit.SECONDS);
            try (var ignored = ExecutionContextScope.open(b)) {
                assertThat(executor.submit(
                        () -> ExecutionContextScope.requireCurrent().user().userId()).get()).isEqualTo(2L);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void nestedScopesRestoreCallerState() {
        MDC.put("request", "outer");
        try (var outer = ExecutionContextScope.open(context(1))) {
            try (var inner = ExecutionContextScope.open(context(2))) {
                assertThat(ExecutionContextScope.requireCurrent().user().userId()).isEqualTo(2L);
            }
            assertThat(ExecutionContextScope.requireCurrent().user().userId()).isEqualTo(1L);
        }
        assertThat(MDC.get("request")).isEqualTo("outer");
        assertThat(MDC.get("userId")).isNull();
        MDC.clear();
    }
}
