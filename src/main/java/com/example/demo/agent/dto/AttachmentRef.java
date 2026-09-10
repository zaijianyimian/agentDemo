package com.example.demo.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 远程 Agent 可读取的附件事实引用，不包含邮箱凭据。 */
public record AttachmentRef(
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size") Long size,
        @JsonProperty("file_path") String filePath,
        @JsonProperty("content_id") String contentId,
        @JsonProperty("disposition") String disposition) {
}
