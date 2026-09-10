package com.example.demo.agent;

/**
 * Java 侧 Agent 运行模式。
 *
 * <p>REMOTE 表示 AI 决策与模型执行全部交给远程 Agent 服务；LEGACY 仅用于过渡期保留
 * Java 本地 LangChain4j / CLI 执行链。</p>
 */
public enum AgentMode {
    REMOTE,
    LEGACY
}
