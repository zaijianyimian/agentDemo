package com.example.demo.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PuzzleCaptchaVerifyRequest {
    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    @Min(value = 0, message = "滑块位置无效")
    @Max(value = 100, message = "滑块位置无效")
    private double sliderPercent;
}

