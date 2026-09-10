package com.example.demo.agent.application;

/** 远程 Agent 暂时不可用，可按策略有限重试。 */
public class AgentUnavailableException extends AgentGatewayException {
    public AgentUnavailableException(String message) { super(message); }
    public AgentUnavailableException(String message, Throwable cause) { super(message, cause); }
}
