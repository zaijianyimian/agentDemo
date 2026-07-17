package com.example.demo.chat.dto;

/**
 * 封装由聊天内容触发业务动作时所需的请求参数。
 * <p>
 * 包含会话来源、消息内容、角色及标题提示信息。
 */
public record ChatActionRequest(
        Long sessionId,
        String content,
        String role,
        String titleHint
) {
}
