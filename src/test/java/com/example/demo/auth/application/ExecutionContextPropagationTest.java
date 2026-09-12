package com.example.demo.auth.application;

import com.example.demo.shared.context.*;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class ExecutionContextPropagationTest {
    private ExecutionContext context(long id) {
        return ExecutionContext.start(new UserContext(id), "test", ExecutionContext.Actor.SYSTEM,
                ExecutionPolicy.readOnly());
    }

    @Test
    void reusesThreadWithoutLeakingAfterSuccessFailureAndCancellation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var a = context(1);
            var b = context(2);
            assertThat(executor.submit(ExecutionContextScope.wrapCallable(a,
                    () -> ExecutionContextScope.requireCurrent().user().userId())).get()).isEqualTo(1L);
            assertThatThrownBy(() -> executor.submit(ExecutionContextScope.wrap(a,
                    () -> { throw new IllegalArgumentException("failed task"); })).get())
                    .isInstanceOf(ExecutionException.class);
            CountDownLatch started = new CountDownLatch(1);
            Future<?> cancelled = executor.submit(ExecutionContextScope.wrap(a, () -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            cancelled.cancel(true);
            executor.submit(() -> {
                assertThatThrownBy(ExecutionContextScope::requireCurrent).isInstanceOf(IllegalStateException.class);
                assertThat(MDC.get("userId")).isNull();
            }).get(5, TimeUnit.SECONDS);
            assertThat(executor.submit(ExecutionContextScope.wrapCallable(b,
                    () -> ExecutionContextScope.requireCurrent().user().userId())).get()).isEqualTo(2L);
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

    @Test
    void reactiveContextSurvivesSchedulerSwitchAndCleansCallbackScope() {
        var scheduler = Schedulers.newSingle("context-test");
        try {
            for (long id : new long[]{1, 2}) {
                var execution = context(id);
                var result = ReactiveExecutionContext.withContext(execution, ctx -> Flux.just(1)
                        .publishOn(scheduler)
                        .flatMap(ignored -> Flux.deferContextual(view -> {
                            var restored = ReactiveExecutionContext.require(view);
                            return Flux.just(restored.user().userId())
                                    .map(ExecutionContextScope.wrapFunction(restored, value -> {
                                        assertThat(ExecutionContextScope.requireCurrent()).isEqualTo(ctx);
                                        return value;
                                    }));
                        })).take(1)).blockLast(Duration.ofSeconds(5));
                assertThat(result).isEqualTo(id);
                Flux.just(1).publishOn(scheduler).doOnNext(ignored -> {
                    assertThatThrownBy(ExecutionContextScope::requireCurrent).isInstanceOf(IllegalStateException.class);
                    assertThat(MDC.get("userId")).isNull();
                }).blockLast(Duration.ofSeconds(5));
            }
        } finally {
            scheduler.dispose();
        }
    }
}
