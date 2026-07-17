package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 邮箱验证码发送结果响应,告知前端冷却秒数与提示文案。
 * <p>
 * 用于在前端按钮上展示倒计时,避免用户在冷却期内重复点击导致请求被拒。
 */
@Data
@Builder
public class EmailCodeSendResponse {
    private int cooldownSeconds;
    private String message;
}
