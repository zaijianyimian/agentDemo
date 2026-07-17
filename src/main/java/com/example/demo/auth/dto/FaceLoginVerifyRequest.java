package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷脸登录二次校验请求体,提交预授权令牌与人脸图片(Base64)。
 * <p>
 * 与密码登录配合:首阶段返回 preAuthToken 后,前端采集人脸并提交本请求,由 FaceAuthService 完成比对签发正式令牌。
 */
@Data
public class FaceLoginVerifyRequest {

    @NotBlank(message = "预认证令牌不能为空")
    private String preAuthToken;

    @NotBlank(message = "人脸图片不能为空")
    private String imageBase64;
}
