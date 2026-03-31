package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.mcp.auto-sync")
public class McpAutoSyncProperties {
    /**
     * 是否启用自动同步
     */
    private boolean enabled = false;

    /**
     * 远程工具清单地址
     */
    private String url = "";

    /**
     * 鉴权 Token（可选）
     */
    private String token = "";

    /**
     * 调度 cron
     */
    private String cron = "0 */5 * * * ?";

    /**
     * 连接超时（秒）
     */
    private int connectTimeoutSeconds = 5;

    /**
     * 读取超时（秒）
     */
    private int readTimeoutSeconds = 15;

    /**
     * 是否同步远端 enabled 状态
     */
    private boolean syncEnabledState = true;

    /**
     * 是否对缺失工具做下线处理
     */
    private boolean disableMissing = true;

    /**
     * 缺失多少个同步周期后自动禁用
     */
    private int missingThreshold = 2;
}

