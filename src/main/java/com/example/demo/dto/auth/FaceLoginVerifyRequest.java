package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FaceLoginVerifyRequest {

    @NotBlank(message = "预认证令牌不能为空")
    private String preAuthToken;

    @NotBlank(message = "人脸图片不能为空")
    private String imageBase64;
}
