package com.example.demo.dispatch.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 派发模块专用线程池，统一注册到 Spring 容器，由 Spring 关闭时调用 {@code shutdown()}
 * 避免 {@code workerPool} / {@code recallExecutor} / {@code fallbackExecutor} 三个
 * 之前各自 {@code newFixedThreadPool} 永不销毁的问题。
 *
 * <p>全部 daemon 线程，JVM 退出也不会阻塞。</p>
 */
@Configuration
public class DispatchExecutors {

    @Bean(name = "dispatchWorkerPool", destroyMethod = "shutdown")
    public ExecutorService dispatchWorkerPool() {
        return Executors.newFixedThreadPool(2, namedDaemonFactory("dispatch-worker"));
    }

    @Bean(name = "dispatchRecallExecutor", destroyMethod = "shutdown")
    public ExecutorService dispatchRecallExecutor() {
        int n = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return Executors.newFixedThreadPool(n, namedDaemonFactory("dispatch-recall"));
    }

    @Bean(name = "dispatchFallbackExecutor", destroyMethod = "shutdown")
    public ExecutorService dispatchFallbackExecutor() {
        return Executors.newSingleThreadExecutor(namedDaemonFactory("dispatch-fallback"));
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}