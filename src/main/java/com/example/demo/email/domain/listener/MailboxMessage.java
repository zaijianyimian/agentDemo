package com.example.demo.email.domain.listener;

import com.example.demo.email.domain.EmailMessage;

/**
 * 邮箱拉取到的一条消息记录。
 * <p>
 * 由 provider 适配器返回，承载去重 key、解析后的 {@link EmailMessage}、拉取后的最新游标以及
 * provider 原生消息对象（用于 IMAP 标记已读等场景）。
 */
public record MailboxMessage(
        MailMessageKey key,
        EmailMessage emailMessage,
        MailCursor cursorAfter,
        Object nativeMessage
) {
}
