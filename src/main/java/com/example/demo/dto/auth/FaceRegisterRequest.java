package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FaceRegisterRequest {

    @NotBlank(message = "人脸图片不能为空")
    private String imageBase64;
}
