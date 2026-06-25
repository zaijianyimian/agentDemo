package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.github")
public class GithubOAuthProperties {

    private boolean enabled = false;
    private String clientId = "";
    private String clientSecret = "";
    private String authorizeUri = "https://github.com/login/oauth/authorize";
    private String tokenUri = "https://github.com/login/oauth/access_token";
    private String userUri = "https://api.github.com/user";
    private String emailsUri = "https://api.github.com/user/emails";
    /**
     * 前端回调地址，例如: http://localhost:3000/oauth/github/callback
     */
    private String redirectUri = "http://localhost:3000/oauth/github/callback";
    private String scope = "read:user user:email";
    private int stateTtlSeconds = 600;
}
