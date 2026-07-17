package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.domain.listener.MailboxMessage;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用 POP3 邮箱适配器。
 * 仅支持轮询模式，按邮件数量（无 UID）维护游标。
 */
@Component
public class GenericPop3MailAdapter implements MailSourceAdapter {

    private final JavaMailSupport javaMailSupport;

    public GenericPop3MailAdapter(JavaMailSupport javaMailSupport) {
        this.javaMailSupport = javaMailSupport;
    }

    @Override
    public MailProvider provider() {
        return MailProvider.GENERIC_POP3;
    }

    @Override
    public boolean supportsListenMode(ListenMode mode) {
        return mode == ListenMode.POLLING;
    }

    @Override
    public List<MailboxMessage> fetchNewMessages(EmailConfig config, MailCursor cursor) {
        Store store = null;
        Folder folder = null;
        try {
            store = javaMailSupport.connectStore(config);
            folder = javaMailSupport.openFolder(store, config, Folder.READ_ONLY);
            long lastSeen = cursor == null ? 0L : cursor.longValue(0L);
            int count = folder.getMessageCount();
            List<MailboxMessage> result = new ArrayList<>();
            for (int i = (int) lastSeen + 1; i <= count; i++) {
                Message message = folder.getMessage(i);
                result.add(new MailboxMessage(
                        javaMailSupport.messageKey(config, provider(), message),
                        javaMailSupport.parseMessage(message, config),
                        MailCursor.of("MESSAGE_COUNT", i),
                        message
                ));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("POP3 拉取新邮件失败: " + e.getMessage(), e);
        } finally {
            javaMailSupport.closeQuietly(folder, false);
            javaMailSupport.closeQuietly(store);
        }
    }
}
