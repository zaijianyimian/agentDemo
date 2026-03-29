package com.example.demo.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编码服务
 * 用于 API Key 的 Base64 编码和解码
 * 注意：Base64 仅用于混淆，不提供真正的加密保护
 */
@Slf4j
@Service
public class EncodingService {

    /**
     * 前缀标记，用于区分已编码和未编码的字符串
     */
    private static final String PREFIX = "B64:";

    /**
     * 编码字符串（使用 Base64）
     */
    public String encode(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        String encoded = Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    /**
     * 解码字符串
     */
    public String decode(String encodedText) {
        if (encodedText == null || encodedText.isEmpty()) {
            return encodedText;
        }

        // 如果有前缀，去掉前缀后解码
        if (encodedText.startsWith(PREFIX)) {
            encodedText = encodedText.substring(PREFIX.length());
        }

        try {
            return new String(Base64.getDecoder().decode(encodedText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decoding failed", e);
            throw new RuntimeException("解码失败: " + e.getMessage());
        }
    }

    /**
     * 检查字符串是否已编码（通过前缀判断）
     */
    public boolean isEncoded(String text) {
        return text != null && text.startsWith(PREFIX);
    }
}