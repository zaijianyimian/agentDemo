package com.example.demo.task.scheduler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务处理器标记注解。
 * <p>
 * 参考 xxl-job 的 {@code @XxlJob} 思路：把 bean 方法标记为可被调度线程路由的 handler。
 * 用法：在任意 {@code @Component} 类的方法上标注，方法签名应为 {@code String handle(Map<String,Object> params)}
 * 或 {@code String handle()}（无参）。
 *
 * <pre>{@code
 * @Component
 * public class MyHandlers {
 *     @JobHandler("sendEmail")
 *     public String sendEmail(Map<String, Object> params) {
 *         // ...
 *         return "ok";
 *     }
 * }
 * }</pre>
 *
 * 注意：当前实现仍以 {@code ScheduledTask.taskType}（SKILL/CHAT/REMINDER）作为主要调度入口，
 * 本注解作为扩展点保留，便于后续把更多能力接入统一的调度器。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobHandler {

    /**
     * handler 名，在调度时通过该名匹配。
     */
    String value();
}