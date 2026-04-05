package com.example.demo.service.security;

import com.example.demo.properties.AuthSecurityProperties;
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

@Service
@RequiredArgsConstructor
public class SensitiveValueCryptoService {

    private static final String PREFIX = "enc::v1::";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final AuthSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

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

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(PREFIX);
    }

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
