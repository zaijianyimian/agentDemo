package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @PasswordStrength(message = "新密码必须包含大小写字母、数字和特殊字符，长度8-64位")
    private String newPassword;
}
