package com.example.demo.service.email;

import com.example.demo.dto.EmailMessage;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import com.example.demo.service.listener.ListenerRuntime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 邮件监听服务
 * 启动时从数据库读取邮箱配置，监听新邮件
 * 使用虚拟线程处理邮件
 */
@Slf4j
@Service
public class EmailListenerService implements ListenerRuntime<EmailConfig> {

    private final EmailConfigMapper emailConfigMapper;
    private final ExecutorService executorService;
    private final EmailAuthConfigService emailAuthConfigService;
    private final EmailConnectionTestService emailConnectionTestService;

    // 存储每个邮箱的Store和Folder连接
    private final Map<Long, Store> storeMap = new ConcurrentHashMap<>();
    private final Map<Long, Folder> folderMap = new ConcurrentHashMap<>();
    private final Map<Long, EmailConfig> configMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSeenUidMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> initialMessageCountMap = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> listenerStartedAtMap = new ConcurrentHashMap<>();
    private final Set<String> processedMessageKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown = false;

    // 邮件处理器（可以注入自定义处理器）
    private EmailHandler emailHandler;

    public EmailListenerService(
            EmailConfigMapper emailConfigMapper,
            EmailAuthConfigService emailAuthConfigService,
            EmailConnectionTestService emailConnectionTestService,
            @Qualifier("emailProcessingExecutor") ExecutorService executorService) {
        this.emailConfigMapper = emailConfigMapper;
        this.emailAuthConfigService = emailAuthConfigService;
        this.emailConnectionTestService = emailConnectionTestService;
        this.executorService = executorService;
    }

    /**
     * 设置邮件处理器
     */
    public void setEmailHandler(EmailHandler handler) {
        this.emailHandler = handler;
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭邮件监听服务...");
        shuttingDown = true;
        stopAllListeners();
        executorService.shutdown();
    }

    /**
     * 从数据库加载邮箱配置并启动监听
     */
    public void loadAndStartListeners() {
        // 查询所有启用的邮箱配置
        List<EmailConfig> configs = emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>()
                        .eq(EmailConfig::getEnabled, true)
        );

        log.info("找到 {} 个启用的邮箱配置", configs.size());

        for (EmailConfig config : configs) {
            try {
                startListener(config);
            } catch (Exception e) {
                log.error("启动邮箱监听失败: {}, 错误: {}", config.getEmail(), e.getMessage());
            }
        }
    }

    /**
     * 启动单个邮箱监听
     */
    @Override
    public void start(EmailConfig config) {
        startListener(config);
    }

    public void startListener(EmailConfig config) {
        emailAuthConfigService.decodeTransientFields(config);
        Long configId = config.getId();

        // 检查是否已有连接：如果 key 存在但连接已死，先清理再重启
        Store existingStore = storeMap.get(configId);
        if (existingStore != null) {
            if (existingStore.isConnected()) {
                log.warn("[{}] 邮箱已在监听中且连接正常", config.getEmail());
                return;
            }
            log.info("[{}] 检测到已断开的旧连接，先清理再重新启动", config.getEmail());
            cleanup(configId);
        }

        executorService.submit(() -> {
            try {
                connectAndListen(config);
            } catch (Exception e) {
                log.error("[{}] 监听异常: {}", config.getEmail(), e.getMessage());
                cleanup(configId);
            }
        });
    }

    /**
     * 连接邮箱并开始监听
     */
    private void connectAndListen(EmailConfig config) throws MessagingException {
        String emailAddr = config.getEmail();
        log.info("[{}] 正在连接邮箱...", emailAddr);

        String protocol = emailConnectionTestService.normalizeProtocol(config.getProtocol());
        String host = safeTrim(config.getHost());
        String email = safeTrim(config.getEmail());
        String folderName = emailConnectionTestService.normalizeFolder(config.getFolder());
        int port = config.getPort() != null ? config.getPort() : 993;
        EmailConnectionTestService.AuthCredential authCredential = emailConnectionTestService.resolveAuthCredential(config, host);

        // 配置邮件属性
        Properties props = emailConnectionTestService.buildMailProperties(config, protocol, host, port, authCredential);

        // 创建Session
        Session session = Session.getInstance(props);
        session.setDebug(false);

        // 连接Store
        Store store = session.getStore(protocol);
        store.connect(host, email, authCredential.secret());

        // 保存连接
        Long configId = config.getId();
        storeMap.put(configId, store);
        configMap.put(configId, config);
        listenerStartedAtMap.put(configId, LocalDateTime.now().minusMinutes(2));

        // 打开文件夹
        Folder folder = openListenerFolder(configId, config, store);
        initializeLastSeenUid(configId, folder);

        log.info("[{}] 连接成功，开始监听文件夹: {}", emailAddr, folderName);

        // 保持连接并轮询（阻塞当前线程）
        keepAlive(configId);
    }

    private Folder openListenerFolder(Long configId, EmailConfig config, Store store) throws MessagingException {
        String folderName = emailConnectionTestService.normalizeFolder(config.getFolder());
        Folder folder = store.getFolder(folderName);
        folder.open(Folder.READ_WRITE);
        folder.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                Message[] messages = e.getMessages();
                for (Message message : messages) {
                    processNewMessage(message, config, "event");
                }
            }
        });
        folderMap.put(configId, folder);
        return folder;
    }

    private Folder refreshListenerFolder(Long configId, EmailConfig config) throws MessagingException {
        Store store = storeMap.get(configId);
        if (store == null || !store.isConnected()) {
            throw new MessagingException("邮箱连接已断开");
        }

        Folder oldFolder = folderMap.remove(configId);
        if (oldFolder != null && oldFolder.isOpen()) {
            try {
                oldFolder.close(false);
            } catch (MessagingException e) {
                log.debug("[{}] 关闭旧Folder失败: {}", config.getEmail(), e.getMessage());
            }
        }

        return openListenerFolder(configId, config, store);
    }

    /**
     * 保持连接并轮询新邮件
     * 每个邮箱在独立的线程中运行此循环
     */
    private void keepAlive(Long configId) {
        EmailConfig config = configMap.get(configId);
        if (config == null) return;

        int intervalSeconds = config.getPollInterval() != null ? config.getPollInterval() : 30;
        String emailAddr = config.getEmail();

        while (storeMap.containsKey(configId)) {
            try {
                Folder folder = refreshListenerFolder(configId, config);
                if (folder != null && folder.isOpen()) {
                    pollNewMessages(configId, folder, config);
                }
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[{}] 轮询线程被中断，退出保活循环", emailAddr);
                break;
            } catch (Exception e) {
                if (shuttingDown) {
                    break;
                }
                log.warn("[{}] 轮询异常: {}", emailAddr, e.getMessage());
                // 清理旧连接，然后由异步任务重新连接，避免当前线程递归调用 connectAndListen
                cleanup(configId);
                submitReconnect(config);
                break;
            }
        }
    }

    private void initializeLastSeenUid(Long configId, Folder folder) {
        try {
            int messageCount = folder.getMessageCount();
            initialMessageCountMap.put(configId, messageCount);
            if (folder instanceof UIDFolder uidFolder) {
                long lastUid = 0L;
                if (messageCount > 0) {
                    lastUid = uidFolder.getUID(folder.getMessage(messageCount));
                }
                lastSeenUidMap.put(configId, lastUid);
                log.info("[{}] 初始化邮件UID游标: {}", configId, lastUid);
            } else {
                lastSeenUidMap.put(configId, (long) folder.getMessageCount());
                log.warn("[{}] 当前邮箱Folder不支持UID，退化为按邮件数量轮询", configId);
            }
        } catch (Exception e) {
            log.warn("[{}] 初始化邮件UID游标失败: {}", configId, e.getMessage());
        }
    }

    private void pollNewMessages(Long configId, Folder folder, EmailConfig config) throws MessagingException {
        int currentCount = folder.getMessageCount();
        if (folder instanceof UIDFolder uidFolder) {
            long lastSeenUid = lastSeenUidMap.getOrDefault(configId, 0L);
            Message[] messages = uidFolder.getMessagesByUID(lastSeenUid + 1, UIDFolder.LASTUID);
            long maxUid = lastSeenUid;
            log.debug("[{}] 邮件轮询: messageCount={}, lastSeenUid={}, uidMatches={}",
                    config.getEmail(), currentCount, lastSeenUid, messages.length);

            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                if (uid <= lastSeenUid) {
                    continue;
                }
                maxUid = Math.max(maxUid, uid);
                processNewMessage(message, config, "poll");
            }

            if (maxUid > lastSeenUid) {
                lastSeenUidMap.put(configId, maxUid);
            }
            scanRecentMessages(configId, folder, config, currentCount);
            return;
        }

        int lastCount = lastSeenUidMap.getOrDefault(configId, 0L).intValue();
        log.debug("[{}] 邮件轮询: messageCount={}, lastCount={}",
                config.getEmail(), currentCount, lastCount);
        if (currentCount <= lastCount) {
            lastSeenUidMap.put(configId, (long) currentCount);
            scanRecentMessages(configId, folder, config, currentCount);
            return;
        }

        for (int messageNumber = lastCount + 1; messageNumber <= currentCount; messageNumber++) {
            processNewMessage(folder.getMessage(messageNumber), config, "poll");
        }
        lastSeenUidMap.put(configId, (long) currentCount);
        scanRecentMessages(configId, folder, config, currentCount);
    }

    private void scanRecentMessages(Long configId, Folder folder, EmailConfig config, int currentCount) throws MessagingException {
        if (currentCount <= 0) {
            return;
        }

        int initialCount = initialMessageCountMap.getOrDefault(configId, currentCount);
        LocalDateTime listenerStartedAt = listenerStartedAtMap.getOrDefault(configId, LocalDateTime.now());
        int start = Math.max(1, currentCount - 30 + 1);
        int handled = 0;

        for (int messageNumber = start; messageNumber <= currentCount; messageNumber++) {
            Message message = folder.getMessage(messageNumber);
            if (message.isSet(Flags.Flag.SEEN)) {
                continue;
            }
            boolean appendedAfterStartup = messageNumber > initialCount;
            boolean receivedAfterStartup = isReceivedAfter(message, listenerStartedAt);
            if (!appendedAfterStartup && !receivedAfterStartup) {
                continue;
            }

            handled++;
            processNewMessage(message, config, "tail-scan");
        }

        if (handled > 0) {
            log.debug("[{}] 末尾扫描命中新邮件候选: {}", config.getEmail(), handled);
        }
    }

    private boolean isReceivedAfter(Message message, LocalDateTime threshold) {
        try {
            Date receivedDate = message.getReceivedDate();
            if (receivedDate == null) {
                receivedDate = message.getSentDate();
            }
            if (receivedDate == null) {
                return false;
            }
            LocalDateTime receivedAt = LocalDateTime.ofInstant(receivedDate.toInstant(), ZoneId.systemDefault());
            return !receivedAt.isBefore(threshold);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提交异步重连任务，避免在 keepAlive 线程中同步递归调用 connectAndListen
     */
    private void submitReconnect(EmailConfig config) {
        if (shuttingDown) {
            return;
        }
        String emailAddr = config.getEmail();
        log.info("[{}] 将在 5 秒后尝试异步重连...", emailAddr);
        executorService.submit(() -> {
            try {
                Thread.sleep(5000);
                // 再次确认没有在监听中（可能被用户手动重启了）
                Store current = storeMap.get(config.getId());
                if (current != null && current.isConnected()) {
                    log.info("[{}] 已有活跃连接，跳过重连", emailAddr);
                    return;
                }
                cleanup(config.getId());
                connectAndListen(config);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("[{}] 异步重连失败: {}", emailAddr, e.getMessage());
                cleanup(config.getId());
            }
        });
    }

    /**
     * 处理新邮件
     */
    private void processNewMessage(Message message, EmailConfig config, String trigger) {
        try {
            if (!isWithinListeningWindow(config)) {
                log.debug("[{}] 当前不在监听时间段内，跳过邮件处理: {}", config.getEmail(), trigger);
                return;
            }

            String messageKey = buildMessageKey(message, config);
            if (!processedMessageKeys.add(messageKey)) {
                log.debug("[{}] 跳过已处理邮件: {}", config.getEmail(), messageKey);
                return;
            }

            EmailMessage emailMessage = parseMessage(message, config);
            log.info("[{}] 收到新邮件({}): {} -> {}", config.getEmail(), trigger, emailMessage.getFrom(), emailMessage.getSubject());

            // 调用处理器
            if (emailHandler != null) {
                emailHandler.handle(emailMessage);
            }
            markMessageSeen(message, config);
        } catch (Exception e) {
            log.error("[{}] 处理邮件失败: {}", config.getEmail(), e.getMessage());
        }
    }

    private void markMessageSeen(Message message, EmailConfig config) {
        try {
            if (!message.isSet(Flags.Flag.SEEN)) {
                message.setFlag(Flags.Flag.SEEN, true);
                log.debug("[{}] 已标记邮件为已读: {}", config.getEmail(), buildMessageKey(message, config));
            }
        } catch (Exception e) {
            log.warn("[{}] 标记邮件已读失败: {}", config.getEmail(), e.getMessage());
        }
    }

    private boolean isWithinListeningWindow(EmailConfig config) {
        LocalTime start = config.getListenStartTime();
        LocalTime end = config.getListenEndTime();
        if (start == null || end == null || start.equals(end)) {
            return true;
        }

        LocalTime now = LocalTime.now();
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private String buildMessageKey(Message message, EmailConfig config) {
        Long configId = config.getId();
        try {
            Folder folder = message.getFolder();
            if (folder instanceof UIDFolder uidFolder) {
                long uid = uidFolder.getUID(message);
                if (uid > 0) {
                    return configId + ":uid:" + uid;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String[] messageIds = message.getHeader("Message-ID");
            if (messageIds != null && messageIds.length > 0 && messageIds[0] != null) {
                return configId + ":message-id:" + messageIds[0];
            }
        } catch (Exception ignored) {
        }

        try {
            return configId + ":fallback:" + message.getSentDate() + ":" + message.getSubject();
        } catch (Exception e) {
            return configId + ":fallback:" + System.identityHashCode(message);
        }
    }

    /**
     * 解析邮件消息
     */
    private EmailMessage parseMessage(Message message, EmailConfig config) throws MessagingException, IOException {
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
                .accountEmail(config.getEmail());

        // 基本信息
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            builder.from(formatAddress(fromAddresses[0]));
            if (fromAddresses[0] instanceof InternetAddress) {
                builder.fromName(decodeMimeText(((InternetAddress) fromAddresses[0]).getPersonal()));
            }
        }

        builder.subject(message.getSubject());

        // 收件人
        Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null) {
            List<String> toList = new ArrayList<>();
            for (Address addr : toAddresses) {
                toList.add(formatAddress(addr));
            }
            builder.to(toList);
        }

        // 抄送
        Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
        if (ccAddresses != null) {
            List<String> ccList = new ArrayList<>();
            for (Address addr : ccAddresses) {
                ccList.add(formatAddress(addr));
            }
            builder.cc(ccList);
        }

        // 时间
        if (message.getSentDate() != null) {
            builder.sentDate(LocalDateTime.ofInstant(
                    message.getSentDate().toInstant(), ZoneId.systemDefault()));
        }
        if (message.getReceivedDate() != null) {
            builder.receivedDate(LocalDateTime.ofInstant(
                    message.getReceivedDate().toInstant(), ZoneId.systemDefault()));
        }

        // 已读状态
        Flags flags = message.getFlags();
        builder.seen(flags.contains(Flags.Flag.SEEN));

        // 解析邮件内容
        parseContent(message, builder);

        return builder.build();
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

    /**
     * 解析邮件内容
     */
    private void parseContent(Part part, EmailMessage.EmailMessageBuilder builder) throws MessagingException, IOException {
        Object content = part.getContent();

        if (content instanceof String) {
            if (part.isMimeType("text/plain")) {
                builder.textContent((String) content);
            } else if (part.isMimeType("text/html")) {
                builder.htmlContent((String) content);
            }
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                parseContent(bodyPart, builder);
            }
        }
    }

    /**
     * 重新连接（已废弃，使用 submitReconnect 异步重连以避免线程递归）
     */
    private void reconnect(Long configId) {
        EmailConfig config = configMap.get(configId);
        if (config == null) return;
        cleanup(configId);
        submitReconnect(config);
    }

    /**
     * 清理单个邮箱资源
     */
    private void cleanup(Long configId) {
        try {
            Folder folder = folderMap.remove(configId);
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception e) {
            log.warn("关闭Folder失败: {}", e.getMessage());
        }

        try {
            Store store = storeMap.remove(configId);
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception e) {
            log.warn("关闭Store失败: {}", e.getMessage());
        }

        configMap.remove(configId);
        lastSeenUidMap.remove(configId);
        initialMessageCountMap.remove(configId);
        listenerStartedAtMap.remove(configId);
    }

    /**
     * 停止所有监听
     */
    @Override
    public void stopAll() {
        stopAllListeners();
    }

    public void stopAllListeners() {
        for (Long configId : new ArrayList<>(storeMap.keySet())) {
            cleanup(configId);
        }
    }

    /**
     * 停止单个邮箱监听
     */
    @Override
    public void stop(Long configId) {
        stopListener(configId);
    }

    public void stopListener(Long configId) {
        EmailConfig config = configMap.get(configId);
        String email = config != null ? config.getEmail() : String.valueOf(configId);
        cleanup(configId);
        log.info("[{}] 已停止邮箱监听", email);
    }

    /**
     * 重载邮箱配置
     */
    public void reloadListeners() {
        stopAllListeners();
        loadAndStartListeners();
    }

    /**
     * 获取监听状态（支持多邮箱）
     * 返回每个邮箱的连接状态和基本信息
     */
    public Map<Long, Map<String, Object>> getListenerStatus() {
        Map<Long, Map<String, Object>> status = new HashMap<>();
        for (Map.Entry<Long, Store> entry : storeMap.entrySet()) {
            Long configId = entry.getKey();
            Store store = entry.getValue();
            EmailConfig config = configMap.get(configId);
            Map<String, Object> item = new HashMap<>();
            item.put("connected", store.isConnected());
            item.put("status", store.isConnected() ? "已连接" : "未连接");
            item.put("email", config != null ? config.getEmail() : "未知");
            item.put("host", config != null ? config.getHost() : "未知");
            status.put(configId, item);
        }
        return status;
    }

    @Override
    public Map<Long, Map<String, Object>> status() {
        return getListenerStatus();
    }

    /**
     * 邮件处理器接口
     */
    public interface EmailHandler {
        void handle(EmailMessage emailMessage);
    }

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }
}
