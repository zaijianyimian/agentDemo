package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Java 到远程 Agent 的聊天请求。 */
public record ChatRequest(
        @JsonProperty("user_id") long userId,
        @JsonProperty("session_id") String sessionId,
        String message) {
}
