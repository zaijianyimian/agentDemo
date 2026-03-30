package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邮箱配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("email_config")
public class EmailConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 邮箱授权码/密码
     */
    private String password;

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
     * 备注
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

    public void setRemark(String remark) {
        this.remark = trim(remark);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
