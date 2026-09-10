package com.example.demo.infrastructure.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Python Graph 服务网关配置。
 *
 * <p>Java 不连接 Graph 使用的 PostgreSQL，只通过内部 HTTP/SSE API 与 Python 通信。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.graph")
public class GraphGatewayProperties {

    /** 是否启用 Python Graph 作为 Agent 主执行入口。 */
    private boolean enabled = false;

    /** Python Graph 服务地址。 */
    private String baseUrl = "http://127.0.0.1:8001";

    /** Java 与 Python 内部调用共享密钥。 */
    private String internalToken = "";

    /** 建立连接超时时间，单位秒。 */
    private int connectTimeoutSeconds = 5;

    /** 普通 HTTP 请求响应超时时间，单位秒。 */
    private int responseTimeoutSeconds = 120;

    /**
     * 启动时验证 Graph 配置，避免打开功能后才在运行期发现内部鉴权或超时配置错误。
     */
    @PostConstruct
    public void validate() {
        if (connectTimeoutSeconds <= 0 || responseTimeoutSeconds <= 0) {
            throw new IllegalStateException("Graph 超时时间必须大于 0 秒");
        }
        if (!enabled) {
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置 app.graph.base-url / GRAPH_BASE_URL");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Graph base-url 格式无效: " + baseUrl, error);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Graph base-url 必须是完整的 http/https 地址: " + baseUrl);
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("启用 Graph 时必须配置 GRAPH_INTERNAL_TOKEN");
        }
    }
}
