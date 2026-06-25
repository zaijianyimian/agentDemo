package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 邮箱配置实体。
 *
 * <p>数据库表 {@code email_config} 的 ORM 映射，承载一个邮箱账号的连接信息、认证信息与监听时段配置。
 * 其中 OAuth2 相关字段为非持久化字段（{@code @TableField(exist = false)}），由
 * {@link com.example.demo.service.email.EmailAuthConfigService} 在读写时序列化到 {@code remark} JSON 中。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("email_config")
public class EmailConfig {

    // ==================== 主键与基础信息 ====================

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    // ==================== 账号与认证 ====================

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 邮箱授权码/密码（密码模式下使用，已加密持久化）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // ==================== OAuth2 扩展字段（非持久化） ====================

    /**
     * 认证方式: password / oauth2_access_token / oauth2_refresh_token
     */
    @TableField(exist = false)
    private String authType;

    /**
     * OAuth2 客户端ID
     */
    @TableField(exist = false)
    private String oauthClientId;

    /**
     * OAuth2 客户端密钥
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String oauthClientSecret;

    /**
     * OAuth2 刷新令牌
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String oauthRefreshToken;

    /**
     * OAuth2 访问令牌（短期，可选）
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String oauthAccessToken;

    /**
     * OAuth2 token 端点（可选）
     */
    @TableField(exist = false)
    private String oauthTokenEndpoint;

    /**
     * OAuth2 scope（可选）
     */
    @TableField(exist = false)
    private String oauthScope;

    /**
     * 是否已保存密码/授权码
     */
    @TableField(exist = false)
    private Boolean passwordConfigured;

    /**
     * 是否已保存 OAuth2 client secret
     */
    @TableField(exist = false)
    private Boolean oauthClientSecretConfigured;

    /**
     * 是否已保存 OAuth2 refresh token
     */
    @TableField(exist = false)
    private Boolean oauthRefreshTokenConfigured;

    /**
     * 是否已保存 OAuth2 access token
     */
    @TableField(exist = false)
    private Boolean oauthAccessTokenConfigured;

    // ==================== 服务器连接信息 ====================

    /**
     * 邮箱服务器主机
     */
    private String host;

    /**
     * 协议类型: imap, pop3
     */
    private String protocol;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 是否启用SSL
     */
    private Boolean sslEnabled;

    // ==================== 监听时段配置 ====================

    /**
     * 是否启用监听
     */
    private Boolean enabled;

    /**
     * 监听文件夹 (默认INBOX)
     */
    private String folder;

    /**
     * 轮询间隔(秒)
     */
    private Integer pollInterval;

    /**
     * 监听开始时间，为空表示全天监听
     */
    private LocalTime listenStartTime;

    /**
     * 监听结束时间，为空表示全天监听
     */
    private LocalTime listenEndTime;

    // ==================== 备注与时间戳 ====================

    /**
     * 备注；密码模式下为自由文本，OAuth2 模式下承载 OAuth 元数据 JSON
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // ==================== Setter 覆写（统一 trim） ====================
    // 显式覆写 Lombok @Data 生成的 setter，在写入时统一去除首尾空白，避免脏数据进入数据库或内存。

    public void setEmail(String email) {
        this.email = trim(email);
    }

    public void setPassword(String password) {
        this.password = trim(password);
    }

    public void setHost(String host) {
        this.host = trim(host);
    }

    public void setProtocol(String protocol) {
        this.protocol = trim(protocol);
    }

    public void setFolder(String folder) {
        this.folder = trim(folder);
    }

    public void setAuthType(String authType) {
        this.authType = trim(authType);
    }

    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = trim(oauthClientId);
    }

    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = trim(oauthClientSecret);
    }

    public void setOauthRefreshToken(String oauthRefreshToken) {
        this.oauthRefreshToken = trim(oauthRefreshToken);
    }

    public void setOauthAccessToken(String oauthAccessToken) {
        this.oauthAccessToken = trim(oauthAccessToken);
    }

    public void setOauthTokenEndpoint(String oauthTokenEndpoint) {
        this.oauthTokenEndpoint = trim(oauthTokenEndpoint);
    }

    public void setOauthScope(String oauthScope) {
        this.oauthScope = trim(oauthScope);
    }

    public void setRemark(String remark) {
        this.remark = trim(remark);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
