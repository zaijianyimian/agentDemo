package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.domain.DispatchedTask;

import java.nio.file.Path;

/**
 * 外部执行器抽象。每个实现 = 一个 CLI 工具的子进程封装。
 */
public interface Executor {

    /**
     * @return 执行器协议名称（与 {@code DispatchedTask.executor} 精确匹配）。
     */
    String hint();

    /**
     * 是否在本机 PATH 上可用。
     */
    boolean isAvailable();

    /**
     * 在 {@code workspace} 目录里执行 Python 已生成的 {@code instruction}。
     *
     * @return 提取自 stdout 的纯文本结果
     * @throws ExecutorUnavailableException CLI 不在 PATH
     * @throws ExecutorTimeoutException    超过超时
     * @throws ExecutorFailedException     执行器以非 0 退出码退出
     */
    String execute(DispatchedTask task, String instruction, Path workspace, int timeoutSeconds);

    /** 执行器找不到 */
    class ExecutorUnavailableException extends RuntimeException {
        public ExecutorUnavailableException(String message) {
            super(message);
        }
    }

    /** 执行超时 */
    class ExecutorTimeoutException extends RuntimeException {
        public ExecutorTimeoutException(String message) {
            super(message);
        }
    }

    /** 执行失败（非 0 退出码） */
    class ExecutorFailedException extends RuntimeException {
        public ExecutorFailedException(String message) {
            super(message);
        }
        public ExecutorFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
