package com.example.demo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * GitHub OAuth 授权引导响应,返回授权地址与 state 凭证的有效期。
 * <p>
 * 由 GithubOAuthService 生成,前端通过 authorizationUrl 跳转 GitHub 完成授权。
 */
@Data
@Builder
public class GithubAuthorizeResponse {
    private String authorizationUrl;
    private int stateExpiresIn;
}
