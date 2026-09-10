package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 远程 Agent 非流式聊天响应。 */
public record ChatResponse(
        String content,
        @JsonProperty("execution_id") String executionId) {
}
