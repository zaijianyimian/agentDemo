package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 受控自治配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.autonomy")
public class AutonomyProperties {

    /**
     * 是否启用自治能力
     */
    private Boolean enabled = true;

    /**
     * 工作区根目录
     */
    private String workspaceRoot = ".";

    /**
     * 生成报告目录
     */
    private String outputDir = "./generated/autonomy";

    /**
     * 是否允许执行后端验证命令
     */
    private Boolean allowBackendVerify = true;

    /**
     * 是否允许执行前端验证命令
     */
    private Boolean allowFrontendVerify = true;

    /**
     * 是否允许直接写入源码目录
     */
    private Boolean allowSourceWrite = false;

    /**
     * 是否允许远程自更新（默认关闭）
     */
    private Boolean allowRemoteUpdate = false;

    /**
     * 外部命令超时时间（秒）
     */
    private Integer commandTimeoutSeconds = 180;
}
