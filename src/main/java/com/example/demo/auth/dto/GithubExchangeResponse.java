package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * GitHub OAuth 换取结果响应,返回系统令牌与登录完成后的跳转路径。
 * <p>
 * 沿用统一的 AuthTokenResponse 作为 token 字段,redirectPath 由 OauthState 携带,登录后跳回原页面。
 */
@Data
@Builder
public class GithubExchangeResponse {
    private AuthTokenResponse token;
    private String redirectPath;
}
