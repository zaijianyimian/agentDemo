package com.example.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 申请重置密码时发送验证码的请求体,接收目标邮箱地址。
 * <p>
 * 由 EmailCodeService 生成 purpose=RESET_PASSWORD 的验证码,引导用户进入密码重置流程。
 */
@Data
public class PasswordResetSendRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}