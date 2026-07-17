package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注册人脸档案的请求体,提交人脸图片(Base64 编码)。
 * <p>
 * FaceAuthService 解析后抽取特征向量并写入 UserFaceProfile,完成刷脸登录能力的开通。
 */
@Data
public class FaceRegisterRequest {

    @NotBlank(message = "人脸图片不能为空")
    private String imageBase64;
}
