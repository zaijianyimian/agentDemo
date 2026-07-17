package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * GitHub OAuth 回调后换取令牌的请求体,提交 GitHub 返回的授权码与本地 state。
 * <p>
 * 由 GithubOAuthService 校验 state 一致性后,用 code 换取 access_token 并完成账号绑定/登录。
 */
@Data
public class GithubExchangeRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;
}
