package com.example.demo.service.email;

import com.example.demo.entity.EmailConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱认证配置编解码服务
 *
 * 为保持向后兼容，不新增数据库字段：
 * - 普通密码模式仍直接使用 email_config.password + remark(纯文本备注)
 * - OAuth2 扩展配置编码到 remark(JSON) 中
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthConfigService {

    public static final String AUTH_TYPE_PASSWORD = "password";
    public static final String AUTH_TYPE_OAUTH2_ACCESS_TOKEN = "oauth2_access_token";
    public static final String AUTH_TYPE_OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";

    private final ObjectMapper objectMapper;

    public void decodeTransientFields(EmailConfig config) {
        if (config == null) {
            return;
        }
        String rawRemark = trim(config.getRemark());
        if (!StringUtils.hasText(rawRemark)) {
            config.setAuthType(AUTH_TYPE_PASSWORD);
            return;
        }

        if (!looksLikeJson(rawRemark)) {
            config.setAuthType(AUTH_TYPE_PASSWORD);
            return;
        }

        try {
            Map<String, Object> meta = objectMapper.readValue(rawRemark, new TypeReference<>() {});
            if (!containsAuthMeta(meta)) {
                config.setAuthType(AUTH_TYPE_PASSWORD);
                return;
            }
            config.setAuthType(normalizeAuthType(asText(meta.get("authType"))));
            config.setOauthClientId(asText(meta.get("oauthClientId")));
            config.setOauthClientSecret(asText(meta.get("oauthClientSecret")));
            config.setOauthRefreshToken(asText(meta.get("oauthRefreshToken")));
            config.setOauthAccessToken(asText(meta.get("oauthAccessToken")));
            config.setOauthTokenEndpoint(asText(meta.get("oauthTokenEndpoint")));
            config.setOauthScope(asText(meta.get("oauthScope")));
            config.setRemark(asText(meta.get("note")));
        } catch (Exception e) {
            // remark 可能是历史文本或用户自定义 JSON，这里不报错，按普通密码模式处理
            config.setAuthType(AUTH_TYPE_PASSWORD);
        }
    }

    public void prepareForPersist(EmailConfig incoming, EmailConfig existing) {
        if (incoming == null) {
            return;
        }

        decodeTransientFields(incoming);
        if (existing != null) {
            decodeTransientFields(existing);
        }

        String authType = normalizeAuthType(firstNonBlank(
                incoming.getAuthType(),
                existing == null ? null : existing.getAuthType()
        ));
        incoming.setAuthType(authType);

        // OAuth 模式下，未提交的新值自动沿用旧值，避免编辑时误清空
        if (!AUTH_TYPE_PASSWORD.equals(authType) && existing != null) {
            incoming.setOauthClientId(firstNonBlank(incoming.getOauthClientId(), existing.getOauthClientId()));
            incoming.setOauthClientSecret(firstNonBlank(incoming.getOauthClientSecret(), existing.getOauthClientSecret()));
            incoming.setOauthRefreshToken(firstNonBlank(incoming.getOauthRefreshToken(), existing.getOauthRefreshToken()));
            incoming.setOauthAccessToken(firstNonBlank(incoming.getOauthAccessToken(), existing.getOauthAccessToken()));
            incoming.setOauthTokenEndpoint(firstNonBlank(incoming.getOauthTokenEndpoint(), existing.getOauthTokenEndpoint()));
            incoming.setOauthScope(firstNonBlank(incoming.getOauthScope(), existing.getOauthScope()));
        }

        String note = trim(incoming.getRemark());
        if (AUTH_TYPE_PASSWORD.equals(authType)
                && !StringUtils.hasText(incoming.getOauthClientId())
                && !StringUtils.hasText(incoming.getOauthRefreshToken())
                && !StringUtils.hasText(incoming.getOauthAccessToken())) {
            // 纯密码模式下保留历史行为：remark 存储为纯文本备注
            incoming.setRemark(note);
            return;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("authType", authType);
        putIfHasText(meta, "note", note);
        putIfHasText(meta, "oauthClientId", incoming.getOauthClientId());
        putIfHasText(meta, "oauthClientSecret", incoming.getOauthClientSecret());
        putIfHasText(meta, "oauthRefreshToken", incoming.getOauthRefreshToken());
        putIfHasText(meta, "oauthAccessToken", incoming.getOauthAccessToken());
        putIfHasText(meta, "oauthTokenEndpoint", incoming.getOauthTokenEndpoint());
        putIfHasText(meta, "oauthScope", incoming.getOauthScope());

        try {
            incoming.setRemark(objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            throw new IllegalStateException("保存邮箱认证扩展配置失败", e);
        }
    }

    private boolean looksLikeJson(String text) {
        String t = trim(text);
        return t != null && t.startsWith("{") && t.endsWith("}");
    }

    private boolean containsAuthMeta(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        return meta.containsKey("authType")
                || meta.containsKey("oauthRefreshToken")
                || meta.containsKey("oauthAccessToken")
                || meta.containsKey("oauthClientId");
    }

    private void putIfHasText(Map<String, Object> data, String key, String value) {
        if (StringUtils.hasText(value)) {
            data.put(key, value.trim());
        }
    }

    private String normalizeAuthType(String authType) {
        String value = trim(authType);
        if (!StringUtils.hasText(value)) {
            return AUTH_TYPE_PASSWORD;
        }
        return switch (value.toLowerCase()) {
            case AUTH_TYPE_OAUTH2_ACCESS_TOKEN -> AUTH_TYPE_OAUTH2_ACCESS_TOKEN;
            case AUTH_TYPE_OAUTH2_REFRESH_TOKEN -> AUTH_TYPE_OAUTH2_REFRESH_TOKEN;
            default -> AUTH_TYPE_PASSWORD;
        };
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        return trim(String.valueOf(value));
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : trim(fallback);
    }

    private String trim(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
