package com.example.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通过邮箱验证码重置密码的请求体,提交邮箱、验证码与新密码。
 * <p>
 * 由 AuthService 校验验证码通过后更新密码哈希并自增 tokenVersion,使历史令牌失效。
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度需在 8 到 128 位之间")
    private String newPassword;
}