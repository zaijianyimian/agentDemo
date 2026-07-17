package com.example.demo.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 拼图验证码校验请求体,提交验证码标识与用户拖动滑块的百分比位置。
 * <p>
 * 由 PuzzleCaptchaService 比对位置偏差并签发一次性 ticket,后续登录/注册请求需带上 ticket 证明已通过人机校验。
 */
@Data
public class PuzzleCaptchaVerifyRequest {
    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    @Min(value = 0, message = "滑块位置无效")
    @Max(value = 100, message = "滑块位置无效")
    private double sliderPercent;
}

