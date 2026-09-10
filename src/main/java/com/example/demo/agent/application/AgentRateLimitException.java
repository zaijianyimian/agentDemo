package com.example.demo.agent.application;

/** 远程 Agent 限流，可按退避策略有限重试。 */
public class AgentRateLimitException extends AgentGatewayException {
    public AgentRateLimitException(String message) { super(message); }
}
