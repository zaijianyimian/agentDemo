package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.application.EmailListenerService;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.persistence.AttachmentStorageService;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JavaMailSupportTest {

    private final EmailAuthConfigService authConfigService = mock(EmailAuthConfigService.class);
    private final AttachmentStorageService attachmentStorageService = mock(AttachmentStorageService.class);
    private final JavaMailSupport support = new JavaMailSupport(authConfigService, attachmentStorageService);
    private final Map<String, String> clientId = Map.of(
            "name", "AgentDemo", "version", "1.0", "vendor", "AgentDemo");

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void listenerIdentifiesNetEaseClientAfterLogin(boolean angus) throws Exception {
        Store store = imapStore(angus);
        Session session = mock(Session.class);
        when(session.getStore("imap")).thenReturn(store);
        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);

            assertThat(support.connectStore(config("imap.163.com"))).isSameAs(store);

            InOrder order = inOrder(store);
            order.verify(store).connect("imap.163.com", "test@163.com", "test-only-secret");
            verifyId(order, store);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionTestIdentifiesClientBeforeOpeningFolder(boolean angus) throws Exception {
        Store store = imapStore(angus);
        Folder folder = mock(Folder.class);
        when(store.getFolder("TEST_ONLY")).thenReturn(folder);
        when(folder.getMessageCount()).thenReturn(1);
        Session session = mock(Session.class);
        when(session.getStore("imap")).thenReturn(store);
        EmailListenerService service = spy(new EmailListenerService(
                mock(EmailConfigMapper.class), mock(EmailListenerManager.class), authConfigService,
                new EmailListenerConfigSupport(), new ObjectMapper(),
                mock(ApplicationEventPublisher.class), attachmentStorageService, mock(ExecutorService.class)));
        doReturn(new EmailListenerService.NetworkCheckResult(true, "ok", 0, null, null))
                .when(service).checkNetworkConnectivity("imap.163.com", 993, 10000);
        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);

            assertThat(service.testConnection(config("imap.163.com")).isSuccess()).isTrue();

            InOrder order = inOrder(store, folder);
            order.verify(store).connect("imap.163.com", "test@163.com", "test-only-secret");
            verifyId(order, store);
            order.verify(store).getFolder("TEST_ONLY");
            order.verify(folder).open(Folder.READ_ONLY);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"imap.gmail.com", "imap.qq.com", "imap.example.com"})
    void unrelatedProvidersKeepTheirExistingConnectionBehavior(String host) throws Exception {
        com.sun.mail.imap.IMAPStore store = mock(com.sun.mail.imap.IMAPStore.class);
        Session session = mock(Session.class);
        when(session.getStore("imap")).thenReturn(store);
        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);

            support.connectStore(config(host));

            verify(store, never()).id(any());
        }
    }

    @Test
    void failedIdentificationClosesConnectionAndDoesNotHideFailure() throws Exception {
        com.sun.mail.imap.IMAPStore store = mock(com.sun.mail.imap.IMAPStore.class);
        MessagingException failure = new MessagingException("ID rejected");
        doThrow(failure).when(store).id(clientId);

        assertThatThrownBy(() -> JavaMailSupport.identifyImapClient(store, "imap.163.com"))
                .isSameAs(failure);
        verify(store).close();
    }

    private Store imapStore(boolean angus) {
        return angus ? mock(org.eclipse.angus.mail.imap.IMAPStore.class)
                : mock(com.sun.mail.imap.IMAPStore.class);
    }

    private void verifyId(InOrder order, Store store) throws Exception {
        if (store instanceof com.sun.mail.imap.IMAPStore imap) {
            order.verify(imap).id(clientId);
        } else if (store instanceof org.eclipse.angus.mail.imap.IMAPStore imap) {
            order.verify(imap).id(clientId);
        }
    }

    private EmailConfig config(String host) {
        return EmailConfig.builder().email("test@163.com").password("test-only-secret")
                .authType("password").host(host).protocol("imap").port(993)
                .sslEnabled(true).folder("TEST_ONLY").build();
    }
}
