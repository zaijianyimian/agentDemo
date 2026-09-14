package com.example.demo.infrastructure.config;

import com.example.demo.shared.context.ContextPropagatingExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置
 * 如果 Java 21+ 则使用虚拟线程，否则使用优化的线程池
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * 检测是否支持虚拟线程
     */
    private static final boolean VIRTUAL_THREAD_SUPPORTED = isVirtualThreadSupported();

    private static boolean isVirtualThreadSupported() {
        try {
            // Java 21+ 支持
            Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 创建线程执行器
     * Java 21+ 使用虚拟线程，否则使用优化的线程池
     */
    @Bean
    public ExecutorService taskExecutor() {
        ExecutorService executor;
        if (VIRTUAL_THREAD_SUPPORTED) {
            try {
                executor = (ExecutorService) Executors.class
                        .getMethod("newVirtualThreadPerTaskExecutor")
                        .invoke(null);
            } catch (Exception e) {
                executor = createOptimizedThreadPool();
            }
        } else {
            executor = createOptimizedThreadPool();
        }
        return new ContextPropagatingExecutorService(executor);
    }

    /**
     * 创建优化的线程池（Java 8 兼容）
     */
    private ExecutorService createOptimizedThreadPool() {
        return Executors.newCachedThreadPool(new NamedThreadFactory("async-task"));
    }

    /**
     * Spring Async 任务执行器
     * 用于 @Async 注解
     */
    @Bean("virtualThreadTaskExecutor")
    public AsyncTaskExecutor virtualThreadTaskExecutor() {
        return new TaskExecutorAdapter(taskExecutor());
    }

    /**
     * 邮件处理专用线程池
     */
    @Bean("emailProcessingExecutor")
    public ExecutorService emailProcessingExecutor() {
        ExecutorService executor;
        if (VIRTUAL_THREAD_SUPPORTED) {
            try {
                executor = (ExecutorService) Executors.class
                        .getMethod("newVirtualThreadPerTaskExecutor")
                        .invoke(null);
            } catch (Exception e) {
                executor = Executors.newCachedThreadPool(new NamedThreadFactory("email"));
            }
        } else {
            executor = Executors.newCachedThreadPool(new NamedThreadFactory("email"));
        }
        return new ContextPropagatingExecutorService(executor);
    }

    /**
     * 工具执行专用线程池
     */
    @Bean("toolExecutionExecutor")
    public ExecutorService toolExecutionExecutor() {
        ExecutorService executor;
        if (VIRTUAL_THREAD_SUPPORTED) {
            try {
                executor = (ExecutorService) Executors.class
                        .getMethod("newVirtualThreadPerTaskExecutor")
                        .invoke(null);
            } catch (Exception e) {
                executor = Executors.newCachedThreadPool(new NamedThreadFactory("tool"));
            }
        } else {
            executor = Executors.newCachedThreadPool(new NamedThreadFactory("tool"));
        }
        return new ContextPropagatingExecutorService(executor);
    }

    /**
     * 日程通知专用线程池
     */
    @Bean("scheduleNotificationExecutor")
    public ExecutorService scheduleNotificationExecutor() {
        ExecutorService executor;
        if (VIRTUAL_THREAD_SUPPORTED) {
            try {
                executor = (ExecutorService) Executors.class
                        .getMethod("newVirtualThreadPerTaskExecutor")
                        .invoke(null);
            } catch (Exception e) {
                executor = Executors.newCachedThreadPool(new NamedThreadFactory("schedule"));
            }
        } else {
            executor = Executors.newCachedThreadPool(new NamedThreadFactory("schedule"));
        }
        return new ContextPropagatingExecutorService(executor);
    }

    /**
     * 命名线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
