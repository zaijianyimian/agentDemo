package com.example.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送邮箱验证码请求体,接收目标邮箱地址。
 * <p>
 * 由 EmailCodeService 处理,内部根据 purpose 与冷却时间控制发送频率,生成验证码并写入 AuthEmailCode 表。
 */
@Data
public class EmailCodeSendRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}