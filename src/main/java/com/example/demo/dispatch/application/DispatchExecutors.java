package com.example.demo.dispatch.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** LEGACY 本地 dispatch 专用线程池。 */
@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class DispatchExecutors {

    @Bean(name = "dispatchWorkerPool", destroyMethod = "shutdown")
    public ExecutorService dispatchWorkerPool() {
        return Executors.newFixedThreadPool(2, namedDaemonFactory("dispatch-worker"));
    }

    @Bean(name = "dispatchRecallExecutor", destroyMethod = "shutdown")
    public ExecutorService dispatchRecallExecutor() {
        int count = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return Executors.newFixedThreadPool(count, namedDaemonFactory("dispatch-recall"));
    }

    @Bean(name = "dispatchFallbackExecutor", destroyMethod = "shutdown")
    public ExecutorService dispatchFallbackExecutor() {
        return Executors.newSingleThreadExecutor(namedDaemonFactory("dispatch-fallback"));
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
