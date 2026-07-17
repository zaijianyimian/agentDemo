package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.persistence.AttachmentStorageService;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JavaMailSupportTest {

    private final EmailAuthConfigService authConfigService = mock(EmailAuthConfigService.class);
    private final AttachmentStorageService attachmentStorageService = mock(AttachmentStorageService.class);
    private final JavaMailSupport support = new JavaMailSupport(authConfigService, attachmentStorageService);

    @Test
    void normalizesJavaMailMessageAndBuildsMessageIdKey() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("sender@example.com", "Sender"));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "to@example.com");
        message.setSubject("Hello");
        message.setText("plain body");
        message.setHeader("Message-ID", "<m-1@example.com>");
        message.setSentDate(new Date());
        message.saveChanges();
        EmailConfig config = EmailConfig.builder().id(7L).email("account@example.com").build();

        EmailMessage normalized = support.parseMessage(message, config);

        assertThat(normalized.getAccountEmail()).isEqualTo("account@example.com");
        assertThat(normalized.getFrom()).contains("sender@example.com");
        assertThat(normalized.getTo()).contains("to@example.com");
        assertThat(normalized.getSubject()).isEqualTo("Hello");
        assertThat(normalized.getTextContent()).isEqualTo("plain body");
        assertThat(support.messageKey(config, MailProvider.GENERIC_POP3, message).stableKey())
                .contains("7:GENERIC_POP3:message-id:");
    }
}
