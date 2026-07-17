package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.persistence.AttachmentStorageService;
import com.example.demo.email.domain.listener.MailMessageKey;
import com.example.demo.email.domain.listener.MailProvider;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * JavaMail 辅助工具。
 * 集中处理 Store/Folder 连接、Message 解析、去重 key 生成以及国内邮箱（163/126/QQ 等）的协议属性补丁。
 */
@Component
public class JavaMailSupport {

    private final EmailAuthConfigService authConfigService;
    private final AttachmentStorageService attachmentStorageService;

    public JavaMailSupport(EmailAuthConfigService authConfigService,
                           AttachmentStorageService attachmentStorageService) {
        this.authConfigService = authConfigService;
        this.attachmentStorageService = attachmentStorageService;
    }

    public Store connectStore(EmailConfig config) throws MessagingException {
        authConfigService.decodeTransientFields(config);
        String protocol = normalizeProtocol(config.getProtocol());
        String host = safeTrim(config.getHost());
        String email = safeTrim(config.getEmail());
        String secret = resolveSecret(config);

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", config.getPort());
        props.put("mail." + protocol + ".connectiontimeout", 10000);
        props.put("mail." + protocol + ".timeout", 10000);

        if (Boolean.TRUE.equals(config.getSslEnabled())) {
            props.put("mail." + protocol + ".ssl.enable", "true");
            props.put("mail." + protocol + ".ssl.trust", host);
        }
        applyAuthProperties(props, protocol, config);
        applyVendorSpecificProperties(props, protocol, host);

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(host, email, secret);
        return store;
    }

    public Folder openFolder(Store store, EmailConfig config, int mode) throws MessagingException {
        Folder folder = store.getFolder(normalizeFolder(config.getFolder()));
        folder.open(mode);
        return folder;
    }

    public EmailMessage parseMessage(Message message, EmailConfig config) throws MessagingException, IOException {
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .accountEmail(config.getEmail());

        String[] messageIds = message.getHeader("Message-ID");
        if (messageIds != null && messageIds.length > 0) {
            builder.messageId(messageIds[0]);
        }

        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            builder.from(formatAddress(fromAddresses[0]));
            if (fromAddresses[0] instanceof InternetAddress internetAddress) {
                builder.fromName(decodeMimeText(internetAddress.getPersonal()));
            }
        }

        builder.subject(message.getSubject());
        builder.to(formatAddresses(message.getRecipients(Message.RecipientType.TO)));
        builder.cc(formatAddresses(message.getRecipients(Message.RecipientType.CC)));

        if (message.getSentDate() != null) {
            builder.sentDate(LocalDateTime.ofInstant(message.getSentDate().toInstant(), ZoneId.systemDefault()));
        }
        if (message.getReceivedDate() != null) {
            builder.receivedDate(LocalDateTime.ofInstant(message.getReceivedDate().toInstant(), ZoneId.systemDefault()));
        }
        Flags flags = message.getFlags();
        builder.seen(flags.contains(Flags.Flag.SEEN));

        List<EmailMessage.Attachment> attachments = new ArrayList<>();
        parseContent(message, builder, attachments, builder.build().getMessageId(), config);
        builder.attachments(attachments);
        return builder.build();
    }

    public MailMessageKey messageKey(EmailConfig config, MailProvider provider, Message message) {
        try {
            Folder folder = message.getFolder();
            if (folder instanceof UIDFolder uidFolder) {
                long uid = uidFolder.getUID(message);
                if (uid > 0) {
                    return MailMessageKey.of(config, provider, "uid:" + uid);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String[] messageIds = message.getHeader("Message-ID");
            if (messageIds != null && messageIds.length > 0 && StringUtils.hasText(messageIds[0])) {
                return MailMessageKey.of(config, provider, "message-id:" + messageIds[0]);
            }
        } catch (Exception ignored) {
        }
        try {
            return MailMessageKey.bestEffort(config, provider,
                    String.valueOf(message.getSentDate()),
                    String.valueOf(message.getFrom() == null ? "" : List.of(message.getFrom())),
                    message.getSubject());
        } catch (Exception e) {
            return MailMessageKey.bestEffort(config, provider, String.valueOf(System.identityHashCode(message)));
        }
    }

    public long uidOrNumber(Folder folder, Message message) throws MessagingException {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder.getUID(message);
        }
        return message.getMessageNumber();
    }

    public void closeQuietly(Folder folder, boolean expunge) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(expunge);
            }
        } catch (Exception ignored) {
        }
    }

    public void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            if (store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private String resolveSecret(EmailConfig config) {
        String authType = config.getAuthType();
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(authType)) {
            return config.getOauthAccessToken();
        }
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(authType)) {
            throw new IllegalArgumentException("协议邮箱 refresh token 模式请先通过 API provider 或旧连接测试刷新 access token");
        }
        return config.getPassword();
    }

    private void applyAuthProperties(Properties props, String protocol, EmailConfig config) {
        String prefix = "mail." + protocol + ".";
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(config.getAuthType())) {
            props.put(prefix + "auth.mechanisms", "XOAUTH2");
            props.put(prefix + "auth.login.disable", "true");
            props.put(prefix + "auth.plain.disable", "true");
        } else {
            props.put(prefix + "auth.login.disable", "false");
            props.put(prefix + "auth.plain.disable", "false");
        }
    }

    private void applyVendorSpecificProperties(Properties props, String protocol, String host) {
        if (!StringUtils.hasText(host)) {
            return;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String prefix = "mail." + protocol + ".";
        if (lowerHost.contains("163.com") || lowerHost.contains("126.com")
                || lowerHost.contains("188.com") || lowerHost.contains("yeah.net")) {
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            props.put(prefix + "ssl.trust", "*");
            props.put(prefix + "usesocketchannels", "true");
            props.put(prefix + "sasl.enable", "false");
            props.put(prefix + "peek", "true");
        }
        if (lowerHost.contains("qq.com") || lowerHost.contains("exmail.qq.com")) {
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            props.put(prefix + "ssl.trust", "*");
            props.put(prefix + "connectiontimeout", 15000);
            props.put(prefix + "timeout", 15000);
        }
    }

    private List<String> formatAddresses(Address[] addresses) {
        if (addresses == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Address address : addresses) {
            values.add(formatAddress(address));
        }
        return values;
    }

    private String formatAddress(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            return internetAddress.toUnicodeString();
        }
        return decodeMimeText(address == null ? null : address.toString());
    }

    private String decodeMimeText(String text) {
        if (text == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(text);
        } catch (Exception e) {
            return text;
        }
    }

    private void parseContent(Part part, EmailMessage.EmailMessageBuilder builder,
                              List<EmailMessage.Attachment> attachments, String messageId, EmailConfig config)
            throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            if (part.isMimeType("text/plain")) {
                builder.textContent(text);
            } else if (part.isMimeType("text/html")) {
                builder.htmlContent(text);
            }
            return;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                parseContent(bodyPart, builder, attachments, messageId, config);
            }
            return;
        }
        if (part instanceof BodyPart bodyPart) {
            try {
                String disposition = bodyPart.getDisposition();
                String fileName = bodyPart.getFileName();
                String[] contentIdHeaders = bodyPart.getHeader("Content-ID");
                String contentId = (contentIdHeaders == null || contentIdHeaders.length == 0) ? null : contentIdHeaders[0];
                boolean hasFileName = fileName != null && !fileName.isBlank();
                boolean isInline = Part.INLINE.equalsIgnoreCase(disposition);
                boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition);

                if (!isAttachment && !isInline && !hasFileName && contentId == null) {
                    return;
                }
                if (isInline && bodyPart.isMimeType("text/plain") && !hasFileName) {
                    return;
                }
                EmailMessage.Attachment attachment = attachmentStorageService.save(bodyPart, messageId, config.getEmail());
                attachments.add(attachment);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(JavaMailSupport.class)
                        .warn("[{}] 附件落盘失败: file={}, err={}",
                                config.getEmail(), bodyPart.getFileName(), e.getMessage());
            }
        }
    }

    private String normalizeProtocol(String protocol) {
        String value = safeTrim(protocol);
        return value == null || value.isBlank() ? "imap" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeFolder(String folder) {
        String value = safeTrim(folder);
        return value == null || value.isBlank() ? "INBOX" : value;
    }

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }
}
