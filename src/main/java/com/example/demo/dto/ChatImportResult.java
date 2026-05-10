package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天记录导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatImportResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 导入的消息数量
     */
    private int importedCount;

    /**
     * 解析出的会话数量
     */
    private int sessionCount;

    /**
     * 来源平台
     */
    private String platform;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 解析出的会话ID列表
     */
    private List<String> sessionIds;
}