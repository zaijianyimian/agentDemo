package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 远程 Agent 标准错误响应；远端未遵循该格式时 Java 仍按 HTTP 状态分类。 */
public record AgentErrorResponse(
        String code,
        String message,
        @JsonProperty("request_id") String requestId) {
}
