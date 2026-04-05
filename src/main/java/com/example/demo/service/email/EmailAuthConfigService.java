package com.example.demo.service.email;

import com.example.demo.entity.EmailConfig;
import com.example.demo.service.security.SensitiveValueCryptoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮箱认证配置编解码服务
 *
 * 为保持向后兼容，不新增数据库字段：
 * - password 字段继续存储邮箱密码/授权码，但改为加密保存
 * - remark 字段仍承载备注与 OAuth2 扩展配置，但敏感值改为加密保存
 */
@Service
@RequiredArgsConstructor
public class EmailAuthConfigService {

    public static final String AUTH_TYPE_PASSWORD = "password";
    public static final String AUTH_TYPE_OAUTH2_ACCESS_TOKEN = "oauth2_access_token";
    public static final String AUTH_TYPE_OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";

    private final ObjectMapper objectMapper;
    private final SensitiveValueCryptoService cryptoService;

    public void decodeTransientFields(EmailConfig config) {
        if (config == null) {
            return;
        }

        String decryptedPassword = trim(cryptoService.decryptIfNeeded(config.getPassword()));
        config.setPassword(decryptedPassword);
        config.setPasswordConfigured(StringUtils.hasText(decryptedPassword));

        String rawRemark = trim(config.getRemark());
        if (!StringUtils.hasText(rawRemark)) {
            config.setAuthType(AUTH_TYPE_PASSWORD);
            clearOauthFields(config);
            return;
        }

        if (!looksLikeJson(rawRemark)) {
            config.setAuthType(AUTH_TYPE_PASSWORD);
            clearOauthFields(config);
            config.setRemark(rawRemark);
            return;
        }

        try {
            Map<String, Object> meta = objectMapper.readValue(rawRemark, new TypeReference<>() {});
            if (!containsAuthMeta(meta)) {
                config.setAuthType(AUTH_TYPE_PASSWORD);
                clearOauthFields(config);
                config.setRemark(rawRemark);
                return;
            }

            config.setAuthType(normalizeAuthType(asText(meta.get("authType"))));
            config.setOauthClientId(asText(meta.get("oauthClientId")));
            config.setOauthClientSecret(trim(cryptoService.decryptIfNeeded(asText(meta.get("oauthClientSecret")))));
            config.setOauthRefreshToken(trim(cryptoService.decryptIfNeeded(asText(meta.get("oauthRefreshToken")))));
            config.setOauthAccessToken(trim(cryptoService.decryptIfNeeded(asText(meta.get("oauthAccessToken")))));
            config.setOauthTokenEndpoint(asText(meta.get("oauthTokenEndpoint")));
            config.setOauthScope(asText(meta.get("oauthScope")));
            config.setRemark(asText(meta.get("note")));

            config.setOauthClientSecretConfigured(meta.containsKey("oauthClientSecret"));
            config.setOauthRefreshTokenConfigured(meta.containsKey("oauthRefreshToken"));
            config.setOauthAccessTokenConfigured(meta.containsKey("oauthAccessToken"));
        } catch (Exception ignored) {
            // remark 可能是历史文本或用户自定义 JSON，这里不报错，按普通密码模式处理
            config.setAuthType(AUTH_TYPE_PASSWORD);
            clearOauthFields(config);
            config.setRemark(rawRemark);
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

        if (AUTH_TYPE_PASSWORD.equals(authType)) {
            String password = firstNonBlank(
                    incoming.getPassword(),
                    existing == null ? null : existing.getPassword()
            );
            if (StringUtils.hasText(password)) {
                incoming.setPassword(cryptoService.encryptIfNeeded(password));
            } else {
                incoming.setPassword(null);
            }
            incoming.setRemark(trim(incoming.getRemark()));
            clearOauthFields(incoming);
            return;
        }

        incoming.setPassword(null);

        // OAuth 模式下，未提交的新值自动沿用旧值，避免编辑时误清空
        if (existing != null) {
            incoming.setOauthClientId(firstNonBlank(incoming.getOauthClientId(), existing.getOauthClientId()));
            incoming.setOauthClientSecret(firstNonBlank(incoming.getOauthClientSecret(), existing.getOauthClientSecret()));
            incoming.setOauthRefreshToken(firstNonBlank(incoming.getOauthRefreshToken(), existing.getOauthRefreshToken()));
            incoming.setOauthAccessToken(firstNonBlank(incoming.getOauthAccessToken(), existing.getOauthAccessToken()));
            incoming.setOauthTokenEndpoint(firstNonBlank(incoming.getOauthTokenEndpoint(), existing.getOauthTokenEndpoint()));
            incoming.setOauthScope(firstNonBlank(incoming.getOauthScope(), existing.getOauthScope()));
        }

        String note = trim(incoming.getRemark());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("authType", authType);
        putIfHasText(meta, "note", note);
        putIfHasText(meta, "oauthClientId", incoming.getOauthClientId());
        putIfHasText(meta, "oauthClientSecret", encryptSensitive(incoming.getOauthClientSecret()));
        putIfHasText(meta, "oauthRefreshToken", encryptSensitive(incoming.getOauthRefreshToken()));
        putIfHasText(meta, "oauthAccessToken", encryptSensitive(incoming.getOauthAccessToken()));
        putIfHasText(meta, "oauthTokenEndpoint", incoming.getOauthTokenEndpoint());
        putIfHasText(meta, "oauthScope", incoming.getOauthScope());

        try {
            incoming.setRemark(objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            throw new IllegalStateException("保存邮箱认证扩展配置失败", e);
        }
    }

    public void sanitizeForResponse(EmailConfig config) {
        if (config == null) {
            return;
        }
        decodeTransientFields(config);
        config.setPassword(null);
        config.setOauthClientSecret(null);
        config.setOauthRefreshToken(null);
        config.setOauthAccessToken(null);
    }

    private String encryptSensitive(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return cryptoService.encryptIfNeeded(value.trim());
    }

    private void clearOauthFields(EmailConfig config) {
        config.setOauthClientId(null);
        config.setOauthClientSecret(null);
        config.setOauthRefreshToken(null);
        config.setOauthAccessToken(null);
        config.setOauthTokenEndpoint(null);
        config.setOauthScope(null);
        config.setOauthClientSecretConfigured(false);
        config.setOauthRefreshTokenConfigured(false);
        config.setOauthAccessTokenConfigured(false);
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
                || meta.containsKey("oauthClientId")
                || meta.containsKey("oauthClientSecret");
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
