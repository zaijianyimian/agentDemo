package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 刷脸能力状态响应,告知前端当前账号是否已注册、是否启用及档案的维度与质量信息。
 * <p>
 * 用于登录页/个人中心判断是否需要引导用户先注册人脸或提示质量过低重新采集。
 */
@Data
@Builder
public class FaceStatusResponse {
    private boolean enrolled;
    private boolean required;
    private boolean enabled;
    private Integer vectorDimension;
    private Double qualityScore;
}
