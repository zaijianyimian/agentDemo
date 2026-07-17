package com.example.demo.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 系统用户主账号实体,保存用户登录凭证、角色、状态及安全相关字段(如 tokenVersion)。
 * <p>
 * 是 AuthService、TokenVersionValidationFilter 等核心组件的数据源;支持账号/邮箱/刷脸登录,tokenVersion 用于在凭据变更时强制下线历史令牌。
 */
@TableName("user_account")
public class UserAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    private String displayName;

    private String role;

    private Boolean enabled;

    private Boolean emailVerified;

    private Boolean faceAuthEnabled;

    private Integer tokenVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
