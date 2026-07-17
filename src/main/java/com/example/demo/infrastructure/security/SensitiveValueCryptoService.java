package com.example.demo.infrastructure.security;

import com.example.demo.infrastructure.properties.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加密服务
 * 使用 AES-256-GCM 算法对敏感字段（如邮箱密码、Token 等）进行可逆加密。
 * 密钥派生自 app.security.data-secret（或 jwtSecret）的 SHA-256 摘要。
 * 输出格式：enc::v1:: + base64(IV || ciphertext)，自动带前缀避免重复加密。
 */
@Service
@RequiredArgsConstructor
public class SensitiveValueCryptoService {

    private static final String PREFIX = "enc::v1::";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final AuthSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 若值非空且未加密，则使用 AES-GCM 加密；否则原样返回
     */
    public String encryptIfNeeded(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (isEncrypted(value)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("敏感信息加密失败", e);
        }
    }

    /**
     * 若值带加密前缀则解密，否则原样返回
     */
    public String decryptIfNeeded(String value) {
        if (!StringUtils.hasText(value) || !isEncrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文格式非法");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感信息解密失败", e);
        }
    }

    /**
     * 判断字符串是否已加密（通过 enc::v1:: 前缀识别）
     */
    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(PREFIX);
    }

    /**
     * 生成脱敏预览字符串（前缀+****+后缀），用于前端展示
     */
    public String preview(String value) {
        String plain = decryptIfNeeded(value);
        if (!StringUtils.hasText(plain)) {
            return "";
        }
        String trimmed = plain.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        if (trimmed.length() <= 8) {
            return trimmed.substring(0, 2) + "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private SecretKeySpec encryptionKey() {
        String secret = securityProperties.resolveDataSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("请设置 app.security.data-secret 或 app.security.jwt-secret");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化敏感信息加密密钥失败", e);
        }
    }
}
