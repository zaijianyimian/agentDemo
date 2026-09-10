package com.example.demo.agent.domain;

/** Java 只关心的业务级 Agent 任务状态，不映射远程 Graph 内部节点。 */
public enum AgentTaskStatus {
    PENDING,
    DISPATCHING,
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
