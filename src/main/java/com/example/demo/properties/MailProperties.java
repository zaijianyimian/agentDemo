package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件发送配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /**
     * SMTP服务器地址
     */
    private String smtpHost;

    /**
     * SMTP端口
     */
    private Integer smtpPort;

    /**
     * SMTP用户名（邮箱地址）
     */
    private String smtpUsername;

    /**
     * SMTP密码或授权码
     */
    private String smtpPassword;

    /**
     * 是否启用SSL
     */
    private Boolean smtpSslEnabled = true;

    /**
     * 发件人名称
     */
    private String fromName = "AI Agent";
}