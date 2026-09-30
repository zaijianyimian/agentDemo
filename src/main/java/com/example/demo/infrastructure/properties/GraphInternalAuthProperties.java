package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部服务间调用认证配置。
 *
 * <p>Python Agent Engine 通过这些配置向 Java 发起受控调用。内部接口不能只依赖
 * {@code X-User-Id} 这类可猜测的头部：必须同时校验共享密钥。共享密钥未配置时
 * 内部接口一律拒绝（fail-closed），不会以空密钥放行任何请求；这样既不会拖垮
 * 未启用该链路的本地开发，也不会在生产上留下静默的鉴权缺口。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.graph")
public class GraphInternalAuthProperties {

    /**
     * 内部调用共享密钥。
     *
     * <p>留空表示未启用内部调用，Java 侧相关端点会返回 503 并记录明确原因。
     * 不得写入仓库默认值，必须由环境变量提供。</p>
     */
    private String internalToken = "";

    /** 内部调用允许的时钟偏移秒数，用于共享密钥签名校验。 */
    private long internalTokenToleranceSeconds = 300;

    /** 判断内部调用是否已配置。 */
    public boolean isConfigured() {
        return internalToken != null && internalToken.length() >= 32;
    }
}
