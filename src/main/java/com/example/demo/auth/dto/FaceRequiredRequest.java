package com.example.demo.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 设置账号是否强制刷脸登录的请求体,用于用户在个人中心开启或关闭二次因素。
 * <p>
 * FaceAuthService 根据该开关更新 UserAccount.faceAuthEnabled,影响后续登录流程是否下发 preAuthToken。
 */
@Data
public class FaceRequiredRequest {
    @NotNull(message = "required 不能为空")
    private Boolean required;
}
