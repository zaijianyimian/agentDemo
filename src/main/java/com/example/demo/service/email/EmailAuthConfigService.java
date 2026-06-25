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
 * 邮箱认证配置编解码服务。
 *
 * <p>负责在持久化形态（密码字段加密、OAuth 元数据塞进 {@code remark} JSON）与运行时明文形态
 * （{@link EmailConfig} 上的临时字段）之间转换，并提供响应脱敏。</p>
 *
 * <p>为保持向后兼容，不新增数据库字段：</p>
 * <ul>
 *   <li>{@code password} 字段继续存储邮箱密码/授权码，但改为加密保存</li>
 *   <li>{@code remark} 字段仍承载备注与 OAuth2 扩展配置，但敏感值改为加密保存</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmailAuthConfigService {

    public static final String AUTH_TYPE_PASSWORD = "password";
    public static final String AUTH_TYPE_OAUTH2_ACCESS_TOKEN = "oauth2_access_token";
    public static final String AUTH_TYPE_OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";

    private final ObjectMapper objectMapper;
    private final SensitiveValueCryptoService cryptoService;

    // ==================== 公开方法：明文 ↔ 持久化互转 ====================

    /**
     * 从持久化形态解码为运行时明文。
     * <p>解密 {@code password} 字段；若 {@code remark} 是合法 JSON，则从中提取 OAuth2 元数据并填充到临时字段。
     * 调用后 {@link EmailConfig} 上同时持有明文凭据与 {@code *Configured} 标志位，</p>
     */
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

    /**
     * 将运行时明文形态转换为持久化形态。
     * <p>加密敏感字段，把 OAuth2 元数据序列化回 {@code remark} JSON；空字段沿用 {@code existing} 已有值，
     * 避免编辑时把未提交的 OAuth 字段误清空。</p>
     *
     * @param incoming 当前请求中的明文配置（会被就地修改）
     * @param existing 数据库中已有的配置（用于补全未提交字段），新增时可为 null
     */
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

    /**
     * 在响应前清空敏感字段（密码、OAuth client secret / refresh token / access token），
     * 仅保留 {@code *Configured} 标志位供前端判断是否已配置。
     */
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

    // ==================== 私有工具 ====================

    /**
     * 对非空敏感值做加密（trim 后再加密，避免空白字符导致密文不稳定）。
     */
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

    /**
     * 快速判断字符串是否像 JSON 对象（仅做首尾 {@code {}} 检查，避免无谓的反序列化）。
     */
    private boolean looksLikeJson(String text) {
        String t = trim(text);
        return t != null && t.startsWith("{") && t.endsWith("}");
    }

    /**
     * 判断 JSON 映射是否包含任意 OAuth2 相关键，决定走 OAuth 解码分支还是普通密码分支。
     */
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

    /**
     * 仅在值非空时写入映射，并自动 trim，避免空串污染 JSON。
     */
    private void putIfHasText(Map<String, Object> data, String key, String value) {
        if (StringUtils.hasText(value)) {
            data.put(key, value.trim());
        }
    }

    /**
     * 归一化认证方式枚举；未知或空值默认 {@link #AUTH_TYPE_PASSWORD}，避免外部脏数据击穿 switch。
     */
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

    /**
     * 把任意值安全地转为 trim 后的字符串；null 返回 null。
     */
    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        return trim(String.valueOf(value));
    }

    /**
     * 返回首个非空字符串；都为 null/空时返回 null。
     */
    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : trim(fallback);
    }

    /**
     * trim 并把空串归一为 null，便于上层统一判空。
     */
    private String trim(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }
}
