package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.MailProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * OAuth2 access token 解析服务。
 * 根据邮箱认证方式直接取 access token，或用 refresh token 调 token endpoint 换新 token，供 API 适配器使用。
 */
@Service
public class OAuthAccessTokenService {

    private final EmailAuthConfigService authConfigService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OAuthAccessTokenService(EmailAuthConfigService authConfigService, ObjectMapper objectMapper, WebClient.Builder builder) {
        this.authConfigService = authConfigService;
        this.objectMapper = objectMapper;
        this.webClient = builder.build();
    }

    public String resolveAccessToken(EmailConfig config, MailProvider provider) {
        authConfigService.decodeTransientFields(config);
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(config.getAuthType())) {
            requireText(config.getOauthAccessToken(), "OAuth2 access token 不能为空");
            return config.getOauthAccessToken();
        }
        if (!EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(config.getAuthType())) {
            throw new IllegalArgumentException(provider + " 真实 API 接入需要 OAuth2 access token 或 refresh token");
        }

        String endpoint = firstText(config.getOauthTokenEndpoint(), defaultTokenEndpoint(provider));
        String scope = firstText(config.getOauthScope(), defaultScope(provider));
        requireText(endpoint, "OAuth2 token endpoint 不能为空");
        requireText(config.getOauthClientId(), "OAuth2 clientId 不能为空");
        requireText(config.getOauthRefreshToken(), "OAuth2 refresh token 不能为空");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", config.getOauthRefreshToken());
        form.add("client_id", config.getOauthClientId());
        if (StringUtils.hasText(config.getOauthClientSecret())) {
            form.add("client_secret", config.getOauthClientSecret());
        }
        if (StringUtils.hasText(scope)) {
            form.add("scope", scope);
        }

        String body = webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            Map<String, Object> payload = objectMapper.readValue(body, new TypeReference<>() {});
            String accessToken = payload.get("access_token") == null ? null : String.valueOf(payload.get("access_token"));
            requireText(accessToken, "OAuth2 token 刷新响应中缺少 access_token");
            return accessToken;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 OAuth2 token 响应失败", e);
        }
    }

    private String defaultTokenEndpoint(MailProvider provider) {
        return switch (provider) {
            case GMAIL_API -> "https://oauth2.googleapis.com/token";
            case MICROSOFT_GRAPH -> "https://login.microsoftonline.com/common/oauth2/v2.0/token";
            default -> null;
        };
    }

    private String defaultScope(MailProvider provider) {
        return switch (provider) {
            case GMAIL_API -> "https://www.googleapis.com/auth/gmail.modify";
            case MICROSOFT_GRAPH -> "offline_access https://graph.microsoft.com/Mail.Read https://graph.microsoft.com/Mail.ReadWrite";
            default -> null;
        };
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : fallback;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
