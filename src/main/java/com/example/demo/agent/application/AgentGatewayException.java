package com.example.demo.agent.application;

/** 远程 Agent 调用统一异常基类。 */
public class AgentGatewayException extends RuntimeException {
    public AgentGatewayException(String message) { super(message); }
    public AgentGatewayException(String message, Throwable cause) { super(message, cause); }
}
