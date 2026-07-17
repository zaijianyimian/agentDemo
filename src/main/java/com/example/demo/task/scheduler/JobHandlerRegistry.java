package com.example.demo.task.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描 Spring 上下文中所有 bean 的 {@link JobHandler} 方法，建立 {@code name -> Method} 映射。
 * <p>
 * 类似 xxl-job 的 {@code GlueFactory}：handler 与业务方法解耦，
 * 后续新增任务类型只需写一个标了 {@code @JobHandler("xxx")} 的方法，无需改调度器。
 */
@Slf4j
@Component
public class JobHandlerRegistry implements BeanPostProcessor {

    private final Map<String, Method> handlers = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            JobHandler annotation = method.getAnnotation(JobHandler.class);
            if (annotation == null) {
                continue;
            }
            method.setAccessible(true);
            handlers.put(annotation.value(), method);
            log.info("注册 JobHandler: {} -> {}.{}", annotation.value(), bean.getClass().getSimpleName(), method.getName());
        }
        return bean;
    }

    /**
     * 根据 handler 名获取方法。
     */
    public Method getHandler(String name) {
        return handlers.get(name);
    }

    /**
     * 当前注册的 handler 名集合（只读视图）。
     */
    public Map<String, Method> snapshot() {
        return Collections.unmodifiableMap(handlers);
    }
}