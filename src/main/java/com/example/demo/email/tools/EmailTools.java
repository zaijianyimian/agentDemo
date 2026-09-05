package com.example.demo.email.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.application.listener.JavaMailSupport;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailMessage;
import com.example.demo.email.persistence.EmailConfigMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 给 AI 使用的只读邮箱查询工具。 */
@Component("emailTools")
@RequiredArgsConstructor
public class EmailTools {

    private static final int MAX_RESULTS = 20;
    private static final int MAX_SCAN = 100;
    private static final int MAX_BODY_LENGTH = 4000;

    private final EmailConfigMapper configMapper;
    private final EmailAuthConfigService authConfigService;
    private final EmailListenerConfigSupport configSupport;
    private final JavaMailSupport javaMailSupport;

    @Tool("""
            列出可供读取的已启用邮箱账号。
            当用户没有指定邮箱，或你不确定应该读取哪个邮箱时先调用此工具。
            只返回邮箱地址、协议和文件夹，不返回密码、token 等凭据。
            """)
    public String list_email_accounts() {
        List<EmailConfig> configs = configMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>().eq(EmailConfig::getEnabled, true));
        if (configs.isEmpty()) {
            return "没有已启用的邮箱账号。";
        }
        StringBuilder result = new StringBuilder("可读取的邮箱账号：\n");
        for (EmailConfig config : configs) {
            configSupport.applyDefaults(config);
            result.append("- ").append(config.getEmail())
                    .append(" | protocol=").append(config.getProtocol())
                    .append(" | folder=").append(config.getFolder())
                    .append('\n');
        }
        return result.toString();
    }

    @Tool("""
            读取邮箱中的最近邮件，按新到旧返回，不修改已读状态，也不会下载附件。
            可按邮箱账号、是否只看未读、主题/发件人/正文关键词过滤。
            用户说“看看最近邮件”“有没有某人的邮件”“读取未读邮件”时使用。
            """)
    public String read_recent_emails(
            @P("邮箱地址；为空时使用第一个已启用账号") String accountEmail,
            @P("返回数量，1 到 20；为空时默认 5") String limit,
            @P("是否只读取未读邮件，true/false；为空时默认 false") String unreadOnly,
            @P("可选关键词，匹配主题、发件人或正文；为空表示不过滤") String keyword) {
        EmailConfig config = resolveConfig(accountEmail);
        if (config == null) {
            return accountEmail == null || accountEmail.isBlank()
                    ? "没有已启用的邮箱账号。"
                    : "未找到已启用邮箱账号: " + accountEmail;
        }

        configSupport.applyDefaults(config);
        authConfigService.decodeTransientFields(config);
        int resultLimit = parseLimit(limit);
        boolean onlyUnread = Boolean.parseBoolean(safe(unreadOnly));
        String normalizedKeyword = safe(keyword).toLowerCase(Locale.ROOT);

        Store store = null;
        Folder folder = null;
        try {
            store = javaMailSupport.connectStore(config);
            folder = javaMailSupport.openFolder(store, config, Folder.READ_ONLY);
            int total = folder.getMessageCount();
            int start = Math.max(1, total - MAX_SCAN + 1);
            Message[] messages = total == 0 ? new Message[0] : folder.getMessages(start, total);
            List<EmailMessage> matched = new ArrayList<>();

            for (int i = messages.length - 1; i >= 0 && matched.size() < resultLimit; i--) {
                Message message = messages[i];
                if (onlyUnread && message.getFlags().contains(Flags.Flag.SEEN)) {
                    continue;
                }
                EmailMessage email = javaMailSupport.parseMessageWithoutAttachments(message, config);
                if (!normalizedKeyword.isBlank() && !matches(email, normalizedKeyword)) {
                    continue;
                }
                matched.add(email);
            }
            return format(config.getEmail(), total, matched);
        } catch (Exception e) {
            return "读取邮箱失败: " + e.getMessage();
        } finally {
            javaMailSupport.closeQuietly(folder, false);
            javaMailSupport.closeQuietly(store);
        }
    }

    private EmailConfig resolveConfig(String accountEmail) {
        LambdaQueryWrapper<EmailConfig> query = new LambdaQueryWrapper<EmailConfig>()
                .eq(EmailConfig::getEnabled, true)
                .orderByAsc(EmailConfig::getId)
                .last("LIMIT 1");
        if (accountEmail != null && !accountEmail.isBlank()) {
            query.eq(EmailConfig::getEmail, accountEmail.trim());
        }
        return configMapper.selectOne(query);
    }

    private boolean matches(EmailMessage email, String keyword) {
        return safe(email.getSubject()).toLowerCase(Locale.ROOT).contains(keyword)
                || safe(email.getFrom()).toLowerCase(Locale.ROOT).contains(keyword)
                || safe(email.getTextContent()).toLowerCase(Locale.ROOT).contains(keyword)
                || safe(email.getHtmlContent()).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String format(String account, int total, List<EmailMessage> messages) {
        if (messages.isEmpty()) {
            return "邮箱 " + account + " 中没有符合条件的邮件。";
        }
        StringBuilder result = new StringBuilder()
                .append("邮箱 ").append(account).append(" 共 ").append(total)
                .append(" 封，返回 ").append(messages.size()).append(" 封：\n\n");
        for (int i = 0; i < messages.size(); i++) {
            EmailMessage email = messages.get(i);
            String body = safe(email.getTextContent());
            if (body.isBlank()) {
                body = stripHtml(safe(email.getHtmlContent()));
            }
            result.append("## ").append(i + 1).append(". ").append(safe(email.getSubject())).append('\n')
                    .append("- 发件人: ").append(safe(email.getFrom())).append('\n')
                    .append("- 时间: ").append(email.getReceivedDate() != null
                            ? email.getReceivedDate() : email.getSentDate()).append('\n')
                    .append("- 状态: ").append(Boolean.TRUE.equals(email.getSeen()) ? "已读" : "未读").append('\n')
                    .append("- Message-ID: ").append(safe(email.getMessageId())).append("\n\n")
                    .append(truncate(body, MAX_BODY_LENGTH)).append("\n\n");
        }
        return result.toString();
    }

    private int parseLimit(String value) {
        try {
            return Math.max(1, Math.min(MAX_RESULTS, Integer.parseInt(safe(value))));
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…[已截断]";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
