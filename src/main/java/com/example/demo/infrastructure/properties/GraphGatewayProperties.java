package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
