package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordLoginRequest {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请先完成图形验证")
    private String captchaTicket;
}
