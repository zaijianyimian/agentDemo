package com.example.demo.aiintegration.persistence;

import com.example.demo.aiintegration.infrastructure.AiPostgresClient;
import com.example.demo.email.domain.EmailMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 把需要交给 Python Graph 的邮件副本保存到 AI PostgreSQL。
 *
 * <p>这里只维护 Graph 当前需要的 {@code email_message} 表，不接管 Java 侧 MySQL 业务表。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class AiEmailRepository {

    private static final String PROVIDER = "JAVA_LISTENER";

    private static final String UPSERT_SQL = """
            INSERT INTO email_message (
                provider,
                external_id,
                message_id,
                sender,
                receiver,
                cc,
                subject,
                content,
                html_content,
                received_at,
                status,
                attachment_count
            ) VALUES (
                :provider,
                :externalId,
                :messageId,
                :sender,
                :receiver,
                :cc,
                :subject,
                :content,
                :htmlContent,
                :receivedAt,
                'RECEIVED',
                :attachmentCount
            )
            ON CONFLICT (provider, external_id)
            WHERE provider IS NOT NULL AND external_id IS NOT NULL
            DO UPDATE SET
                message_id = EXCLUDED.message_id,
                sender = EXCLUDED.sender,
                receiver = EXCLUDED.receiver,
                cc = EXCLUDED.cc,
                subject = EXCLUDED.subject,
                content = EXCLUDED.content,
                html_content = EXCLUDED.html_content,
                received_at = EXCLUDED.received_at,
                attachment_count = EXCLUDED.attachment_count,
                updated_at = now()
            RETURNING email_id, status
            """;

    private final AiPostgresClient postgresClient;

    /**
     * 创建 AI 邮件仓储。
     *
     * @param postgresClient AI PostgreSQL 访问入口
     */
    public AiEmailRepository(AiPostgresClient postgresClient) {
        this.postgresClient = postgresClient;
    }

    /**
     * 保存或刷新供 Graph 使用的邮件副本。
     *
     * @param emailMessage Java 邮件监听层解析后的标准邮件
     * @return PostgreSQL 内部邮件 ID 与当前 Agent 状态
     */
    public StoredEmail saveForGraph(EmailMessage emailMessage) {
        Objects.requireNonNull(emailMessage, "emailMessage不能为空");
        if (emailMessage.getFrom() == null || emailMessage.getFrom().isBlank()) {
            throw new IllegalArgumentException("Graph 邮件发件人不能为空");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("provider", PROVIDER)
                .addValue("externalId", buildExternalId(emailMessage))
                .addValue("messageId", blankToNull(emailMessage.getMessageId()))
                .addValue("sender", emailMessage.getFrom().trim())
                .addValue("receiver", joinAddresses(emailMessage.getTo()))
                .addValue("cc", joinAddresses(emailMessage.getCc()))
                .addValue("subject", blankToNull(emailMessage.getSubject()))
                .addValue("content", blankToNull(emailMessage.getTextContent()))
                .addValue("htmlContent", blankToNull(emailMessage.getHtmlContent()))
                .addValue("receivedAt", resolveReceivedAt(emailMessage))
                .addValue("attachmentCount", emailMessage.getAttachments() == null
                        ? 0
                        : emailMessage.getAttachments().size());

        return postgresClient.jdbc().queryForObject(
                UPSERT_SQL,
                params,
                (rs, rowNum) -> new StoredEmail(
                        rs.getLong("email_id"),
                        rs.getString("status")
                )
        );
    }

    private String buildExternalId(EmailMessage emailMessage) {
        String source = String.join(
                "|",
                nullToEmpty(emailMessage.getAccountEmail()),
                nullToEmpty(emailMessage.getMessageId()),
                nullToEmpty(emailMessage.getFrom()),
                nullToEmpty(emailMessage.getSubject()),
                String.valueOf(emailMessage.getSentDate()),
                String.valueOf(emailMessage.getReceivedDate())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private OffsetDateTime resolveReceivedAt(EmailMessage emailMessage) {
        LocalDateTime dateTime = emailMessage.getReceivedDate();
        if (dateTime == null) {
            dateTime = emailMessage.getSentDate();
        }
        if (dateTime == null) {
            dateTime = LocalDateTime.now();
        }
        return dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String joinAddresses(List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        String joined = addresses.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return joined.isBlank() ? null : joined;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * AI PostgreSQL 中邮件的持久化结果。
     *
     * @param emailId Graph 使用的邮件主键
     * @param status 当前 Agent 处理状态
     */
    public record StoredEmail(long emailId, String status) {
    }
}
