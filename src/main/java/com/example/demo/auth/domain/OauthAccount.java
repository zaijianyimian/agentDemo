package com.example.demo.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * 第三方 OAuth 账号绑定实体,记录用户与外部登录提供方(如 GitHub)之间的关联关系。
 * <p>
 * 通过 provider + providerUserId 唯一定位外部账号,userId 与系统用户主账号绑定,用于第三方登录与账号合并场景。
 */
@TableName("oauth_account")
public class OauthAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String provider;

    private String providerUserId;

    private String login;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
