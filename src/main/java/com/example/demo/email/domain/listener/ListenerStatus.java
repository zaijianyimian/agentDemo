package com.example.demo.email.domain.listener;

/**
 * 邮箱监听运行状态枚举。
 * <p>
 * 描述监听实例当前所处阶段：{@link #STARTING} 启动中、{@link #RUNNING} 正常运行、
 * {@link #FALLBACK} 已降级到备用模式、{@link #STOPPED} 已停止、{@link #ERROR} 异常中断。
 */
public enum ListenerStatus {
    STARTING,
    RUNNING,
    FALLBACK,
    STOPPED,
    ERROR
}
