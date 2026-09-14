package com.example.demo.email.persistence;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.shared.context.CurrentUserContext;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 附件落盘服务：把 JavaMail BodyPart 写入 data/users/{userId}/email-attachments/{messageId}/{safeName}，
 * 返回 {@link EmailMessage.Attachment} 供 {@link com.example.demo.email.domain.EmailMessage} 携带。
 *
 * <p>落盘后即使后续 AI 解析失败，文件本身保留，可在前端 EmailDetail 页下载。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    private final OwnedStorageResolver storage;
    private final CurrentUserContext currentUser;

    /**
     * 把附件 bodyPart 落盘。
     *
     * @param part        JavaMail Part
     * @param messageId   邮件 Message-ID，用作目录隔离
     * @param accountEmail 所属邮箱账号（用于日志）
     * @return 落盘后的 attachment 元数据
     */
    public EmailMessage.Attachment save(BodyPart part, String messageId, String accountEmail)
            throws IOException, MessagingException {
        String originalName = part.getFileName();
        String fileName = sanitizeFileName(originalName);
        String safeMessageId = sanitizeFileName(messageId == null ? "unknown" : messageId);

        long userId = currentUser.requireUserId();
        String storageKey = resolveStorageKey(userId, safeMessageId, fileName);
        Path target = storage.resolveForCreate(
                userId, OwnedStorageResolver.Category.EMAIL_ATTACHMENTS, storageKey);

        long size;
        try (InputStream in = part.getInputStream()) {
            size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Saved attachment: account={}, messageId={}, name={}, size={} bytes, path={}",
                accountEmail, messageId, fileName, size, target);

        return EmailMessage.Attachment.builder()
                .userId(userId)
                .fileName(originalName == null ? fileName : originalName)
                .contentType(part.getContentType())
                .size(size)
                .storageKey(storageKey)
                .contentId(stripCidPrefix(part.getHeader("Content-ID")))
                .disposition(part.getDisposition())
                .build();
    }

    /** Reads an attachment only from the current user's owned attachment root. */
    public byte[] read(String storageKey) throws IOException {
        long userId = currentUser.requireUserId();
        Path source = storage.resolveExisting(
                userId, OwnedStorageResolver.Category.EMAIL_ATTACHMENTS, storageKey);
        return Files.readAllBytes(source);
    }

    /**
     * 计算附件最终落盘路径，处理重名：同目录下同名则追加 -uuid。
     */
    private String resolveStorageKey(long userId, String safeMessageId, String fileName) throws IOException {
        String candidateKey = safeMessageId + "/" + fileName;
        Path candidate = storage.resolveForCreate(
                userId, OwnedStorageResolver.Category.EMAIL_ATTACHMENTS, candidateKey);
        if (!Files.exists(candidate)) {
            return candidateKey;
        }
        int dot = fileName.lastIndexOf('.');
        String stem = dot < 0 ? fileName : fileName.substring(0, dot);
        String ext = dot < 0 ? "" : fileName.substring(dot);
        return safeMessageId + "/" + stem + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    /**
     * 去掉文件名的危险字符和路径分隔符。
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "attachment-" + UUID.randomUUID().toString().substring(0, 8);
        }
        // RFC 2231 / 2047 编码的文件名
        try {
            name = jakarta.mail.internet.MimeUtility.decodeText(name);
        } catch (Exception ignored) {
            // 已经是普通名则忽略
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "attachment";
        }
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200);
        }
        return cleaned;
    }

    private String stripCidPrefix(String[] contentIdHeaders) {
        if (contentIdHeaders == null || contentIdHeaders.length == 0) {
            return null;
        }
        String cid = contentIdHeaders[0];
        if (cid == null) {
            return null;
        }
        String trimmed = cid.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
