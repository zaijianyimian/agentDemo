package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容聊天模型配置属性
 * 绑定 langchain4j.open-ai.chat-model.* 配置项（API Key、Base URL、模型名）
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class OpenAiChatProperties {

    private String apiKey;
    private String baseUrl;
    private String modelName;

    /**
     * 请求和响应可能包含凭据、邮件正文及用户对话，默认禁止写入日志。
     * 仅允许在使用脱敏测试数据的受控排障环境中显式开启。
     */
    private boolean logRequests = false;
    private boolean logResponses = false;
}
