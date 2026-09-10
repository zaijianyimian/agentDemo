package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 远程 Agent 接收任务后的业务级响应。 */
public record AgentTaskAcceptedResponse(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("execution_id") String executionId,
        @JsonProperty("email_id") Long emailId,
        String status,
        boolean duplicate) {

    /**
     * 兼容当前 Python 邮件接口尚未回传 task_id 的阶段。
     *
     * @param fallbackTaskId Java 本地稳定 taskId。
     * @return 带稳定 taskId 的响应。
     */
    public AgentTaskAcceptedResponse withFallbackTaskId(String fallbackTaskId) {
        if (taskId != null && !taskId.isBlank()) {
            return this;
        }
        return new AgentTaskAcceptedResponse(fallbackTaskId, executionId, emailId, status, duplicate);
    }
}
