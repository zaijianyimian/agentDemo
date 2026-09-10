package com.example.demo.agent.application;

/** 远程 Agent 服务鉴权失败，不自动重试。 */
public class AgentUnauthorizedException extends AgentGatewayException {
    public AgentUnauthorizedException(String message) { super(message); }
}
