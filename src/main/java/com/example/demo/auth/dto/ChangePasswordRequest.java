package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 已登录用户修改密码的请求体,要求提交当前密码与符合强度规则的新密码。
 * <p>
 * 当前密码用于二次校验,新密码通过 PasswordStrengthValidator 校验复杂度后由 AuthService 完成更新并自增 tokenVersion。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @PasswordStrength(message = "新密码必须包含大小写字母、数字和特殊字符，长度8-64位")
    private String newPassword;
}
