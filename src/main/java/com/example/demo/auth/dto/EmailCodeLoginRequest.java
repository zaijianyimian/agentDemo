package com.example.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱验证码登录请求体,提交邮箱地址与一次性验证码完成无密码登录。
 * <p>
 * 由 AuthController 接收,AuthService 配合 AuthEmailCode 完成验证码校验并签发令牌。
 */
@Data
public class EmailCodeLoginRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;
}