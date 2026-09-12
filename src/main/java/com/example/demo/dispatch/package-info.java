/**
 * 派发执行模块。
 *
 * <p>从邮件链路产生的可执行单元落库后，本模块负责异步调度、外部执行器（Claude Code / Codex）
 * 调用、工作区管理、纯模板 prompt 拼接、以及按重要性阈值推送执行结果。</p>
 */
@ApplicationModule(
        id = "dispatch",
        displayName = "Dispatch",
        allowedDependencies = {
                "email::*",
                "infrastructure",
                "shared::*",
                "system::*"
        }
)
package com.example.demo.dispatch;

import org.springframework.modulith.ApplicationModule;
