package com.example.demo.dispatch.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 派发模块 worker 线程池，由 Spring 在关闭时调用 {@code shutdown()}。
 *
 * <p>全部 daemon 线程，JVM 退出也不会阻塞。</p>
 */
@Configuration
public class DispatchExecutors {

    @Bean(name = "dispatchWorkerPool", destroyMethod = "shutdown")
    public ExecutorService dispatchWorkerPool() {
        return Executors.newFixedThreadPool(2, namedDaemonFactory("dispatch-worker"));
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
