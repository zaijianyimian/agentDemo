package com.example.demo.agent.application;

/** 远程 Agent 调用超时，可按策略有限重试。 */
public class AgentTimeoutException extends AgentGatewayException {
    public AgentTimeoutException(String message) { super(message); }
    public AgentTimeoutException(String message, Throwable cause) { super(message, cause); }
}
