package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 当前登录用户的公开档案,作为 AuthTokenResponse 中的内嵌字段下发。
 * <p>
 * 仅包含前端展示与权限判断所需的最小字段集,避免泄露密码哈希、token 版本等敏感信息。
 */
@Data
@Builder
public class AuthUserProfile {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private Boolean emailVerified;
}
