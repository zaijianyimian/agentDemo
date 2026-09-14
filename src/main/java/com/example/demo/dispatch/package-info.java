/**
 * 派发执行模块。
 *
 * <p>本模块保存外部 Agent 产生的执行请求与终态结果。没有合格隔离 Worker 时，任务以
 * {@code WORKER_UNAVAILABLE} 安全失败；Java 不启动本机进程或创建工作区。</p>
 */
@ApplicationModule(
        id = "dispatch",
        displayName = "Dispatch",
        allowedDependencies = {
                "auth::application",
                "email::*",
                "infrastructure",
                "shared::*",
                "system::*"
        }
)
package com.example.demo.dispatch;

import org.springframework.modulith.ApplicationModule;
