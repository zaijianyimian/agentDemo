package com.example.demo.agent.application;

/** 远程 Agent 拒绝请求参数，不自动重试。 */
public class AgentBadRequestException extends AgentGatewayException {
    public AgentBadRequestException(String message) { super(message); }
}
