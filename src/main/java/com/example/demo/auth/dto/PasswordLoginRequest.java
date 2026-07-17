package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录请求体,接受用户名或邮箱加密码。
 * <p>
 * AuthService 根据用户名/邮箱定位账号,校验密码哈希后签发令牌;若账号开启刷脸,则进入二次因素流程。
 */
@Data
public class PasswordLoginRequest {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}