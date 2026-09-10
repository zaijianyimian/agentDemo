package com.example.demo.agent;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** Java Agent 边界统一配置。 */
@Data
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    /** 兼容期默认 LEGACY；部署配置推荐显式设置 REMOTE。 */
    private AgentMode mode = AgentMode.LEGACY;

    /** 远程 Agent Gateway 配置。 */
    private Remote remote = new Remote();

    /** @return 当前是否由远程 Agent 接管 AI 执行。 */
    public boolean isRemote() {
        return mode == AgentMode.REMOTE;
    }

    /** 启动时校验 REMOTE 所需配置。 */
    @PostConstruct
    public void validate() {
        if (!isRemote()) {
            return;
        }
        if (remote.connectTimeoutSeconds <= 0 || remote.responseTimeoutSeconds <= 0) {
            throw new IllegalStateException("Agent Gateway 超时时间必须大于 0 秒");
        }
        if (remote.maxRetries < 0 || remote.retryBackoffMillis < 0) {
            throw new IllegalStateException("Agent Gateway 重试配置不能小于 0");
        }
        if (remote.baseUrl == null || remote.baseUrl.isBlank()) {
            throw new IllegalStateException("REMOTE 模式必须配置 app.agent.remote.base-url");
        }
        URI uri;
        try {
            uri = URI.create(remote.baseUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Agent Gateway base-url 格式无效: " + remote.baseUrl, error);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Agent Gateway base-url 必须是完整 http/https 地址: " + remote.baseUrl);
        }
        if (remote.internalToken == null || remote.internalToken.isBlank()) {
            throw new IllegalStateException("REMOTE 模式必须配置 AGENT_INTERNAL_TOKEN");
        }
    }

    /** 远程 Agent 服务连接参数。 */
    @Data
    public static class Remote {
        private String baseUrl = "http://127.0.0.1:8001";
        private String internalToken = "";
        private int connectTimeoutSeconds = 5;
        private int responseTimeoutSeconds = 120;
        private int maxRetries = 2;
        private long retryBackoffMillis = 300L;
    }
}
