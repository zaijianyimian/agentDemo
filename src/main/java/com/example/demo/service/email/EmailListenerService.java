package com.example.demo.service.email;

import com.example.demo.dto.EmailMessage;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * 邮件监听服务。
 *
 * <p>负责实际建立 IMAP/POP3 连接、维护 Folder、轮询新邮件并回调注册到本服务的 {@link EmailObserver}。
 * 每个邮箱账号在注入的 {@code emailProcessingExecutor}（虚拟线程池）中跑一个独立的保活循环；</p>
 *
 * <p>本服务不感知监听时段，启停与时间窗由 {@link EmailListenerScheduleService} 统一调度。
 * 连接断开时本服务会自行异步重连，不需要外部干预。</p>
 */
@Slf4j
@Service
public class EmailListenerService {

    private final EmailConfigMapper emailConfigMapper;
    private final ExecutorService executorService;
    private final EmailAuthConfigService emailAuthConfigService;
    private final ObjectMapper objectMapper;

    // 存储每个邮箱的Store和Folder连接
    private final Map<Long, Store> storeMap = new ConcurrentHashMap<>();
    private final Map<Long, Folder> folderMap = new ConcurrentHashMap<>();
    private final Map<Long, EmailConfig> configMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSeenUidMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> initialMessageCountMap = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> listenerStartedAtMap = new ConcurrentHashMap<>();
    private final Set<String> processedMessageKeys = ConcurrentHashMap.newKeySet();
    private final List<EmailObserver> emailObservers = new CopyOnWriteArrayList<>();
    private volatile boolean shuttingDown = false;

    // 邮件处理器（兼容旧的单处理器注册方式）
    private EmailHandler emailHandler;

    public EmailListenerService(
            EmailConfigMapper emailConfigMapper,
            EmailAuthConfigService emailAuthConfigService,
            ObjectMapper objectMapper,
            @Qualifier("emailProcessingExecutor") ExecutorService executorService) {
        this.emailConfigMapper = emailConfigMapper;
        this.emailAuthConfigService = emailAuthConfigService;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
    }

    /**
     * 设置邮件处理器
     */
    // ==================== 观察者注册 ====================

    /**
     * 设置（覆盖式）单处理器；保留用于兼容旧的单处理器注册方式。
     * 新代码应直接使用 {@link #registerObserver(EmailObserver)}，支持多观察者并存。
     */
    public void setEmailHandler(EmailHandler handler) {
        if (this.emailHandler != null) {
            unregisterObserver(this.emailHandler);
        }
        this.emailHandler = handler;
        if (handler != null) {
            registerObserver(handler);
        }
    }

    /**
     * 注册新邮件观察者；同一观察者重复注册会被忽略。
     */
    public void registerObserver(EmailObserver observer) {
        if (observer != null && !emailObservers.contains(observer)) {
            emailObservers.add(observer);
        }
    }

    /**
     * 注销新邮件观察者；null 安全。
     */
    public void unregisterObserver(EmailObserver observer) {
        if (observer != null) {
            emailObservers.remove(observer);
        }
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
    // ==================== 启停控制 ====================

    /**
     * 从数据库加载所有 {@code enabled=true} 的配置并启动监听。
     * 旧连接仍存活时不会重复启动；启动失败仅记录日志，不影响其他邮箱。
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
    /**
     * 启动单个邮箱的监听。
     * <p>若该邮箱已有活跃连接则跳过；旧连接已断开时会清理后再重启。
     * 实际连接动作异步提交到虚拟线程池，本方法立即返回。</p>
     */
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
    // ==================== 连接与保活 ====================

    /**
     * 实际建立 Store/Folder 连接、注册消息监听器并阻塞进入保活循环。
     * 调用线程会被阻塞到邮箱断开或应用关闭，期间持续轮询。
     */
    private void connectAndListen(EmailConfig config) throws MessagingException {
        String emailAddr = config.getEmail();
        log.info("[{}] 正在连接邮箱...", emailAddr);

        String protocol = normalizeProtocol(config.getProtocol());
        String host = safeTrim(config.getHost());
        String email = safeTrim(config.getEmail());
        String folderName = normalizeFolder(config.getFolder());
        AuthCredential authCredential = resolveAuthCredential(config, host, protocol);

        // 配置邮件属性
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
        applyAuthProperties(props, protocol, authCredential);
        applyVendorSpecificProperties(props, protocol, host);

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

    /**
     * 打开监听 Folder 并注册 {@link MessageCountAdapter}，新邮件到达时通过事件回调处理。
     * 注意 Folder 以 {@code READ_WRITE} 打开，处理成功后会被标记为已读。
     */
    private Folder openListenerFolder(Long configId, EmailConfig config, Store store) throws MessagingException {
        String folderName = normalizeFolder(config.getFolder());
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

    /**
     * 关闭旧 Folder 并重新打开。某些 IMAP 服务器会强制关闭空闲 Folder，定时刷新可保持可用。
     */
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
    /**
     * 保活循环：每隔 {@code pollInterval} 秒刷新 Folder 并轮询新邮件。
     * 任一异常都会触发清理 + 异步重连，避免在持锁线程上递归重连。
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

    // ==================== 轮询与新邮件处理 ====================

    /**
     * 初始化 UID 游标与启动时刻的邮件总数。
     * <p>UID 用于增量轮询；初始邮件数则用于尾扫（{@link #scanRecentMessages}）时排除启动前的旧邮件。</p>
     */
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

    /**
     * 轮询新邮件。
     * <p>支持两种增量方式：Folder 实现 {@link UIDFolder} 时按 UID 拉取；否则按邮件数量差量。
     * 轮询后始终再走一次尾扫以兜底覆盖事件回调漏报的情况。</p>
     */
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

    /**
     * 尾扫最近 30 封未读邮件，处理在监听启动后到达或追加的邮件。
     * 通过「初始邮件总数」与「邮件接收时间」双重过滤，避免重复处理启动前的旧邮件。
     */
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

    /**
     * 判断邮件接收时间是否不早于阈值；优先取 {@code receivedDate}，缺失时回退到 {@code sentDate}。
     */
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
    /**
     * 异步重连：5 秒后重新建立连接。
     * <p>延迟是为了让网络抖动等瞬时故障自行恢复；提交到线程池异步执行是为了避免在保活线程上递归调用导致栈溢出。</p>
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
    /**
     * 处理一封新邮件：监听时段判断 → 去重 → 解析 → 通知观察者 → 标记已读。
     *
     * @param trigger 触发来源，用于日志：{@code event} / {@code poll} / {@code tail-scan}
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

            notifyObservers(emailMessage);
            markMessageSeen(message, config);
        } catch (Exception e) {
            log.error("[{}] 处理邮件失败: {}", config.getEmail(), e.getMessage());
        }
    }

    /**
     * 串行调用所有注册的观察者，单个观察者抛异常不影响其他观察者。
     */
    private void notifyObservers(EmailMessage emailMessage) {
        for (EmailObserver observer : emailObservers) {
            try {
                observer.onNewEmail(emailMessage);
            } catch (Exception e) {
                log.error("邮件观察者处理失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 把邮件标记为已读，避免下次轮询重复处理。失败仅记录警告，不抛出。
     */
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

    /**
     * 当前时间是否在 {@code listenStartTime} ~ {@code listenEndTime} 区间内；支持跨夜区间。
     * 起止为 null 或相等时视为全天监听（始终在区间内）。
     */
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

    /**
     * 生成用于去重的消息 key。优先级：UID → Message-ID → 时间戳+主题。
     * 任何异常都安全降级到下一级，最终兜底为 {@code identityHashCode}。
     */
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
    /**
     * 把 JavaMail {@link Message} 转换为项目内的 {@link EmailMessage} DTO。
     * 仅提取 text/plain 与 text/html 部分，附件不会包含进来。
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

    /**
     * 把 {@link Address} 转为可读字符串（InternetAddress 用 unicode 形式）。
     */
    private String formatAddress(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            return internetAddress.toUnicodeString();
        }
        return decodeMimeText(address == null ? null : address.toString());
    }

    /**
     * 解码 RFC 2047 编码的邮件头（=?UTF-8?B?...?= 等）；失败时原样返回。
     */
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
    /**
     * 递归解析邮件 Part 树，收集 text/plain 与 text/html 内容。Multipart 时逐 BodyPart 递归。
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
    /**
     * 已废弃的同步重连入口，保留仅为不破坏旧调用方。
     * 实际重连已统一改为 {@link #submitReconnect(EmailConfig)} 异步执行。
     */
    @Deprecated
    private void reconnect(Long configId) {
        EmailConfig config = configMap.get(configId);
        if (config == null) return;
        cleanup(configId);
        submitReconnect(config);
    }

    /**
     * 清理单个邮箱资源
     */
    // ==================== 资源清理 ====================

    /**
     * 清理指定邮箱的 Folder/Store 连接及所有相关游标映射。
     * 任意一步异常只记录警告，不影响其他资源释放。
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
    /**
     * 停止所有邮箱监听。在应用关闭或全量重载时使用。
     */
    public void stopAllListeners() {
        for (Long configId : new ArrayList<>(storeMap.keySet())) {
            cleanup(configId);
        }
    }

    /**
     * 停止单个邮箱监听
     */
    /**
     * 停止指定邮箱的监听。会清理连接，关联的时间轮任务由调度服务负责取消。
     */
    public void stopListener(Long configId) {
        EmailConfig config = configMap.get(configId);
        String email = config != null ? config.getEmail() : String.valueOf(configId);
        cleanup(configId);
        log.info("[{}] 已停止邮箱监听", email);
    }

    /**
     * 重载邮箱配置
     */
    /**
     * 重新加载监听：先停所有连接，再从 DB 拉取并启动。
     * 等价于 {@link #stopAllListeners()} + {@link #loadAndStartListeners()}。
     */
    public void reloadListeners() {
        stopAllListeners();
        loadAndStartListeners();
    }

    /**
     * 获取监听状态（支持多邮箱）
     * 返回每个邮箱的连接状态和基本信息
     */
    /**
     * 获取每个邮箱的连接状态快照。
     * <p>仅返回当前仍有 {@link Store} 在 {@code storeMap} 里的邮箱，已被停止的不会出现在结果中。</p>
     *
     * @return key=configId, value={connected, status, email, host}
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

    /**
     * 测试邮箱连接
     * 用于验证邮箱配置是否正确
     */
    // ==================== 测试连接 ====================

    /**
     * 测试单个邮箱的连通性与认证，并返回服务端文件夹的邮件总数。
     * <p>会先做 TCP 网络探测再走完整 IMAP 流程，对 163/126/188/QQ 等国内邮箱的特殊错误做友好提示。</p>
     */
    public EmailTestResult testConnection(EmailConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            emailAuthConfigService.decodeTransientFields(config);
            log.info("测试邮箱连接: {}", config.getEmail());

            String protocol = normalizeProtocol(config.getProtocol());
            String host = safeTrim(config.getHost());
            String email = safeTrim(config.getEmail());
            String folderName = normalizeFolder(config.getFolder());
            int port = config.getPort() != null ? config.getPort() : 993;
            AuthCredential authCredential = resolveAuthCredential(config, host, protocol);

            NetworkCheckResult networkCheckResult = checkNetworkConnectivity(host, port, 10000);
            if (!networkCheckResult.isSuccess()) {
                return new EmailTestResult(
                        false,
                        "连接失败：服务器无法访问邮件服务器，请检查主机、端口或服务器防火墙",
                        System.currentTimeMillis() - startTime,
                        0,
                        networkCheckResult.getErrorDetail()
                );
            }

            // 配置邮件属性
            Properties props = new Properties();
            props.put("mail.store.protocol", protocol);
            props.put("mail." + protocol + ".host", host);
            props.put("mail." + protocol + ".port", port);
            props.put("mail." + protocol + ".connectiontimeout", 10000);
            props.put("mail." + protocol + ".timeout", 10000);

            if (Boolean.TRUE.equals(config.getSslEnabled())) {
                props.put("mail." + protocol + ".ssl.enable", "true");
                props.put("mail." + protocol + ".ssl.trust", host);
            }
            applyAuthProperties(props, protocol, authCredential);
            applyVendorSpecificProperties(props, protocol, host);

            // 创建Session
            Session session = Session.getInstance(props);
            session.setDebug(false);

            // 连接Store
            Store store = session.getStore(protocol);
            store.connect(host, email, authCredential.secret());

            // 测试打开文件夹
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            // 获取邮件数量
            int messageCount = folder.getMessageCount();

            // 关闭连接
            folder.close(false);
            store.close();

            long duration = System.currentTimeMillis() - startTime;
            log.info("邮箱 {} 连接测试成功，耗时 {}ms，文件夹 {} 共有 {} 封邮件",
                    config.getEmail(), duration, folderName, messageCount);

            return new EmailTestResult(true, "连接成功", duration, messageCount, null);

        } catch (AuthenticationFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("邮箱 {} 认证失败: {}", config.getEmail(), e.getMessage());
            String errorMsg = e.getMessage();
            String detail = e.getMessage();

            // 针对 163/126/188 邮箱的特殊错误提示
            if (errorMsg != null) {
                if (errorMsg.contains("Unsafe Login") || errorMsg.contains("LOGIN")) {
                    errorMsg = "认证失败：163/126邮箱需要使用授权码而非登录密码。请在邮箱设置中开启IMAP服务并生成授权码";
                } else if (errorMsg.contains("Too many login")) {
                    errorMsg = "登录频率限制：请稍后再试或检查是否有其他客户端在同时登录";
                } else if (errorMsg.contains("Invalid credentials")) {
                    if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(config.getAuthType())
                            || EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(config.getAuthType())) {
                        errorMsg = "认证失败：OAuth2令牌无效或过期，请更新 access token / refresh token";
                    } else {
                        errorMsg = "认证失败：邮箱地址或密码/授权码错误";
                    }
                }
            }

            return new EmailTestResult(false, errorMsg, duration, 0, detail);

        } catch (MessagingException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("邮箱 {} 连接失败: {}", config.getEmail(), e.getMessage());
            String errorMsg = e.getMessage();
            String detail = e.getMessage();

            if (errorMsg != null) {
                if (errorMsg.contains("Unsafe Login")) {
                    errorMsg = "不安全登录被拒绝：163/126邮箱需要使用授权码而非登录密码。请在邮箱网页版设置中：1.开启IMAP服务 2.生成客户端授权码";
                } else if (errorMsg.contains("connection") || errorMsg.contains("connect")) {
                    errorMsg = "连接失败：无法连接到邮件服务器，请检查服务器地址和端口";
                } else if (errorMsg.contains("SSL") || errorMsg.contains("TLS")) {
                    errorMsg = "SSL/TLS错误：请检查SSL配置是否正确";
                } else if (errorMsg.contains("timed out") || errorMsg.contains("timeout")) {
                    errorMsg = "连接超时：服务器无法访问邮件服务器，请检查网络或防火墙设置";
                }
            } else {
                errorMsg = "邮件服务连接失败";
            }
            return new EmailTestResult(false, errorMsg, duration, 0, detail);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("邮箱 {} 测试异常: {}", config.getEmail(), e.getMessage());
            return new EmailTestResult(false, "测试异常：" + e.getMessage(), duration, 0, e.getMessage());
        }
    }

    /**
     * 邮箱测试结果
     */
    // ==================== 内部类与接口 ====================

    /**
     * {@link #testConnection} 的返回结果。
     */
    public static class EmailTestResult {
        private final boolean success;
        private final String message;
        private final long durationMs;
        private final int messageCount;
        private final String errorDetail;

        public EmailTestResult(boolean success, String message, long durationMs, int messageCount, String errorDetail) {
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
            this.messageCount = messageCount;
            this.errorDetail = errorDetail;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }
        public int getMessageCount() { return messageCount; }
        public String getErrorDetail() { return errorDetail; }
    }

    /**
     * 邮件处理器接口
     */
    /**
     * 新邮件观察者接口。所有新邮件都会回调到已注册的观察者。
     */
    public interface EmailObserver {
        void onNewEmail(EmailMessage emailMessage);
    }

    /**
     * 兼容旧 API 的单处理器接口，等价于实现 {@link EmailObserver}。
     */
    public interface EmailHandler extends EmailObserver {
        void handle(EmailMessage emailMessage);

        @Override
        default void onNewEmail(EmailMessage emailMessage) {
            handle(emailMessage);
        }
    }

    // ==================== 网络探测 ====================

    /**
     * 仅做 TCP 网络层探测（DNS + Socket connect），不进行任何 IMAP 协议交互。
     * 用于配置保存前的连通性预检。
     */
    public NetworkCheckResult checkNetworkConnectivity(String host, Integer port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try {
            String normalizedHost = safeTrim(host);
            int normalizedPort = (port == null || port <= 0) ? 993 : port;
            if (normalizedHost == null || normalizedHost.isEmpty()) {
                return new NetworkCheckResult(false, "主机地址不能为空", 0, null, "host is blank");
            }

            InetAddress address = InetAddress.getByName(normalizedHost);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, normalizedPort), timeoutMs);
            }
            long duration = System.currentTimeMillis() - start;
            return new NetworkCheckResult(
                    true,
                    "网络连通",
                    duration,
                    address.getHostAddress(),
                    null
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new NetworkCheckResult(
                    false,
                    "网络不通",
                    duration,
                    null,
                    e.getMessage()
            );
        }
    }

    /**
     * {@link #checkNetworkConnectivity} 的返回结果。
     */
    public static class NetworkCheckResult {
        private final boolean success;
        private final String message;
        private final long durationMs;
        private final String resolvedIp;
        private final String errorDetail;

        public NetworkCheckResult(boolean success, String message, long durationMs, String resolvedIp, String errorDetail) {
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
            this.resolvedIp = resolvedIp;
            this.errorDetail = errorDetail;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }
        public String getResolvedIp() { return resolvedIp; }
        public String getErrorDetail() { return errorDetail; }
    }

    // ==================== 认证与厂商适配 ====================

    /**
     * 按认证模式写 JavaMail 协议属性。
     * <p>OAuth2 模式只启用 XOAUTH2；密码模式显式启用 LOGIN/PLAIN，
     * 否则 163/126 等国内邮箱会被识别为 "Unsafe Login" 拒绝。</p>
     */
    private void applyAuthProperties(Properties props, String protocol, AuthCredential authCredential) {
        String prefix = "mail." + protocol + ".";

        if (authCredential != null && authCredential.mode() == AuthMode.OAUTH2) {
            props.put(prefix + "auth.mechanisms", "XOAUTH2");
            props.put(prefix + "auth.login.disable", "true");
            props.put(prefix + "auth.plain.disable", "true");
        } else {
            // 163/126/188 等国内邮箱需要显式启用 PLAIN 认证，否则会被识别为 "Unsafe Login"
            props.put(prefix + "auth.login.disable", "false");
            props.put(prefix + "auth.plain.disable", "false");
        }
    }

    /**
     * 根据邮箱服务商应用特定的连接属性
     * 163/126/188/QQ 对 JavaMail 有客户端识别限制，需要额外配置
     */
    /**
     * 163/126/188/yeah/QQ 邮箱对 JavaMail 客户端有兼容性限制，需要写额外属性。
     * 包含强制 SSL、关闭 SASL、信任所有证书、延长超时等。
     */
    private void applyVendorSpecificProperties(Properties props, String protocol, String host) {
        if (!StringUtils.hasText(host)) {
            return;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String prefix = "mail." + protocol + ".";

        // 163 / 126 / 188 / yeah 邮箱的特殊处理
        if (lowerHost.contains("163.com") || lowerHost.contains("126.com")
                || lowerHost.contains("188.com") || lowerHost.contains("yeah.net")) {
            // 强制 SSL，关闭 STARTTLS 混用
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            // 信任所有证书（163 证书链有时不完整）
            props.put(prefix + "ssl.trust", "*");
            // socket channels 改善兼容性
            props.put(prefix + "usesocketchannels", "true");
            // 禁用 SASL，强制 LOGIN + PLAIN
            props.put(prefix + "sasl.enable", "false");
            props.put(prefix + "peek", "true");
            props.put(prefix + "connectionpool.debug", "false");
        }

        // QQ 邮箱 / 腾讯企业邮箱
        if (lowerHost.contains("qq.com") || lowerHost.contains("exmail.qq.com")) {
            // QQ 邮箱 IMAP 必须使用 SSL
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            props.put(prefix + "ssl.trust", "*");
            // QQ 对连接频率有限制，适当延长超时
            props.put(prefix + "connectiontimeout", 15000);
            props.put(prefix + "timeout", 15000);
        }
    }

    /**
     * 根据 {@code authType} 解析出可用的认证凭据。
     * <ul>
     *   <li>access token 模式：直接使用 {@code oauthAccessToken}</li>
     *   <li>refresh token 模式：用 {@code oauthRefreshToken} 调 token endpoint 换 access token</li>
     *   <li>密码模式：使用 {@code password} 字段（实际为授权码）</li>
     * </ul>
     */
    private AuthCredential resolveAuthCredential(EmailConfig config, String host, String protocol) {
        emailAuthConfigService.decodeTransientFields(config);
        String authType = normalizeAuthType(config.getAuthType());

        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equals(authType)) {
            String accessToken = safeTrim(config.getOauthAccessToken());
            if (!StringUtils.hasText(accessToken)) {
                throw new IllegalArgumentException("OAuth2 access token 不能为空");
            }
            return new AuthCredential(AuthMode.OAUTH2, accessToken);
        }

        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equals(authType)) {
            String accessToken = resolveAccessTokenByRefreshToken(config, host);
            return new AuthCredential(AuthMode.OAUTH2, accessToken);
        }

        String password = safeTrim(config.getPassword());
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("密码/授权码不能为空");
        }
        return new AuthCredential(AuthMode.PASSWORD, password);
    }

    /**
     * 使用 refresh token 调用 OAuth2 token endpoint 换取 access token。
     * 缺省 token endpoint / scope 时按 host 后缀猜测（Gmail / Outlook）。
     */
    private String resolveAccessTokenByRefreshToken(EmailConfig config, String host) {
        String refreshToken = safeTrim(config.getOauthRefreshToken());
        String clientId = safeTrim(config.getOauthClientId());
        String clientSecret = safeTrim(config.getOauthClientSecret());
        String tokenEndpoint = safeTrim(config.getOauthTokenEndpoint());
        String scope = safeTrim(config.getOauthScope());

        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("OAuth2 refresh token 不能为空");
        }
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalArgumentException("OAuth2 clientId 不能为空");
        }
        if (!StringUtils.hasText(tokenEndpoint)) {
            tokenEndpoint = defaultTokenEndpointByHost(host);
        }
        if (!StringUtils.hasText(tokenEndpoint)) {
            throw new IllegalArgumentException("无法推断 OAuth2 token endpoint，请在邮箱配置中填写 oauthTokenEndpoint");
        }
        if (!StringUtils.hasText(scope)) {
            scope = defaultScopeByHost(host);
        }

        try {
            String body = buildTokenRequestBody(refreshToken, clientId, clientSecret, scope);
            URL url = new URL(tokenEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String responseBody;
            try (InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream()) {
                responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException(parseTokenErrorMessage(status, responseBody));
            }

            Map<String, Object> payload = objectMapper.readValue(responseBody, new TypeReference<>() {});
            String accessToken = payload.get("access_token") == null ? null : String.valueOf(payload.get("access_token"));
            if (!StringUtils.hasText(accessToken)) {
                throw new IllegalArgumentException("OAuth2 token 刷新响应中缺少 access_token");
            }
            return accessToken;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("OAuth2 token 刷新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拼接 OAuth2 refresh token 交换请求的 form body。clientSecret 与 scope 为空时跳过。
     */
    private String buildTokenRequestBody(String refreshToken, String clientId, String clientSecret, String scope) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        params.put("client_id", clientId);
        if (StringUtils.hasText(clientSecret)) {
            params.put("client_secret", clientSecret);
        }
        if (StringUtils.hasText(scope)) {
            params.put("scope", scope);
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(entry.getKey()))
                    .append('=')
                    .append(urlEncode(entry.getValue()));
        }
        return builder.toString();
    }

    /**
     * 从 OAuth2 错误响应 JSON 中提取 {@code error} / {@code error_description}；非 JSON 时回退到 HTTP 状态。
     */
    private String parseTokenErrorMessage(int status, String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "OAuth2 token 刷新失败，HTTP状态: " + status;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object error = payload.get("error");
            Object description = payload.get("error_description");
            String detail = (error == null ? "" : String.valueOf(error))
                    + (description == null ? "" : (" - " + description));
            if (StringUtils.hasText(detail)) {
                return "OAuth2 token 刷新失败: " + detail.trim();
            }
        } catch (Exception ignored) {
        }
        return "OAuth2 token 刷新失败，HTTP状态: " + status;
    }

    /**
     * 根据 host 后缀推断 OAuth2 token endpoint（仅 Gmail / Outlook 走默认，其他返回 null 让用户自己填）。
     */
    private String defaultTokenEndpointByHost(String host) {
        String normalized = safeTrim(host);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        if (value.contains("gmail.com")) {
            return "https://oauth2.googleapis.com/token";
        }
        if (value.contains("outlook") || value.contains("office365") || value.contains("hotmail") || value.contains("live.com")) {
            return "https://login.microsoftonline.com/common/oauth2/v2.0/token";
        }
        return null;
    }

    /**
     * 根据 host 后缀推断 OAuth2 scope（仅 Gmail / Outlook 提供默认值）。
     */
    private String defaultScopeByHost(String host) {
        String normalized = safeTrim(host);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        if (value.contains("gmail.com")) {
            return "https://mail.google.com/";
        }
        if (value.contains("outlook") || value.contains("office365") || value.contains("hotmail") || value.contains("live.com")) {
            return "offline_access https://outlook.office.com/IMAP.AccessAsUser.All";
        }
        return null;
    }

    /**
     * 归一化认证方式字符串；不识别或为空时默认 {@code password}，避免脏数据击穿 switch。
     */
    private String normalizeAuthType(String authType) {
        if (!StringUtils.hasText(authType)) {
            return EmailAuthConfigService.AUTH_TYPE_PASSWORD;
        }
        String normalized = authType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN -> EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN;
            case EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN -> EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN;
            default -> EmailAuthConfigService.AUTH_TYPE_PASSWORD;
        };
    }

    /**
     * URL 编码（UTF-8），null 视为空串。
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * null-safe trim；null 直接返回 null。
     */
    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    /**
     * 归一化协议名（小写、去空白），空时默认 {@code imap}。
     */
    private String normalizeProtocol(String protocol) {
        String value = safeTrim(protocol);
        return (value == null || value.isEmpty()) ? "imap" : value.toLowerCase();
    }

    /**
     * 归一化监听文件夹名，空时默认 {@code INBOX}。
     */
    private String normalizeFolder(String folder) {
        String value = safeTrim(folder);
        return (value == null || value.isEmpty()) ? "INBOX" : value;
    }

    private enum AuthMode {
        PASSWORD,
        OAUTH2
    }

    private record AuthCredential(AuthMode mode, String secret) {
    }
}
