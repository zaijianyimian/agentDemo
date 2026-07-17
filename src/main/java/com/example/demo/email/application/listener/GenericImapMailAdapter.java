package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 IMAP 邮箱适配器。
 * 面向所有支持 IMAP 协议的邮箱，使用 {@link JavaMailSupport} 拉取新邮件并按 UID 维护游标。
 */
@Component
public class GenericImapMailAdapter implements MailSourceAdapter {

    private final JavaMailSupport javaMailSupport;

    public GenericImapMailAdapter(JavaMailSupport javaMailSupport) {
        this.javaMailSupport = javaMailSupport;
    }

    @Override
    public MailProvider provider() {
        return MailProvider.GENERIC_IMAP;
    }

    @Override
    public boolean supportsListenMode(ListenMode mode) {
        return mode == ListenMode.POLLING || mode == ListenMode.IMAP_IDLE;
    }

    @Override
    public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
        Store store = null;
        Folder folder = null;
        try {
            store = javaMailSupport.connectStore(config);
            folder = javaMailSupport.openFolder(store, config, Folder.READ_WRITE);
            long lastSeen = cursor == null ? 0L : cursor.longValue(0L);
            List<MailboxMessage> result = new ArrayList<>();
            long maxCursor = lastSeen;

            if (folder instanceof UIDFolder uidFolder) {
                Message[] messages = uidFolder.getMessagesByUID(lastSeen + 1, UIDFolder.LASTUID);
                for (Message message : messages) {
                    long uid = uidFolder.getUID(message);
                    if (uid <= lastSeen) {
                        continue;
                    }
                    maxCursor = Math.max(maxCursor, uid);
                    result.add(new MailboxMessage(
                            javaMailSupport.messageKey(config, provider(), message),
                            javaMailSupport.parseMessage(message, config),
                            MailCursor.of("UID", maxCursor),
                            message
                    ));
                }
                return result;
            }

            int count = folder.getMessageCount();
            for (int i = (int) lastSeen + 1; i <= count; i++) {
                Message message = folder.getMessage(i);
                maxCursor = i;
                result.add(new MailboxMessage(
                        javaMailSupport.messageKey(config, provider(), message),
                        javaMailSupport.parseMessage(message, config),
                        MailCursor.of("MESSAGE_COUNT", maxCursor),
                        message
                ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("IMAP 拉取新邮件失败: " + e.getMessage(), e);
        } finally {
            javaMailSupport.closeQuietly(folder, false);
            javaMailSupport.closeQuietly(store);
        }
    }

    @Override
    public void acknowledge(EmailConfig config, MailboxMessage message) {
        if (message != null && message.nativeMessage() instanceof Message nativeMessage) {
            try {
                nativeMessage.setFlag(Flags.Flag.SEEN, true);
            } catch (Exception ignored) {
            }
        }
    }
}
