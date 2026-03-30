package com.example.demo.service.email;

import com.example.demo.dto.EmailMessage;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
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
public class EmailListenerService {

    private final EmailConfigMapper emailConfigMapper;
    private final ExecutorService executorService;

    // 存储每个邮箱的Store和Folder连接
    private final Map<Long, Store> storeMap = new ConcurrentHashMap<>();
    private final Map<Long, Folder> folderMap = new ConcurrentHashMap<>();
    private final Map<Long, EmailConfig> configMap = new ConcurrentHashMap<>();

    // 邮件处理器（可以注入自定义处理器）
    private EmailHandler emailHandler;

    public EmailListenerService(
            EmailConfigMapper emailConfigMapper,
            @Qualifier("emailProcessingExecutor") ExecutorService executorService) {
        this.emailConfigMapper = emailConfigMapper;
        this.executorService = executorService;
    }

    /**
     * 设置邮件处理器
     */
    public void setEmailHandler(EmailHandler handler) {
        this.emailHandler = handler;
    }

    /**
     * 应用启动时初始化邮件监听
     */
    @PostConstruct
    public void init() {
        log.info("初始化邮件监听服务...");
        loadAndStartListeners();
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭邮件监听服务...");
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
    public void startListener(EmailConfig config) {
        if (storeMap.containsKey(config.getId())) {
            log.warn("邮箱 {} 已在监听中", config.getEmail());
            return;
        }

        executorService.submit(() -> {
            try {
                connectAndListen(config);
            } catch (Exception e) {
                log.error("邮箱 {} 监听异常: {}", config.getEmail(), e.getMessage());
                // 清理资源
                cleanup(config.getId());
            }
        });
    }

    /**
     * 连接邮箱并开始监听
     */
    private void connectAndListen(EmailConfig config) throws MessagingException {
        log.info("正在连接邮箱: {}", config.getEmail());

        String protocol = normalizeProtocol(config.getProtocol());
        String host = safeTrim(config.getHost());
        String email = safeTrim(config.getEmail());
        String password = safeTrim(config.getPassword());
        String folderName = normalizeFolder(config.getFolder());

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

        // 创建Session
        Session session = Session.getInstance(props);
        session.setDebug(false);

        // 连接Store
        Store store = session.getStore(protocol);
        store.connect(host, email, password);

        // 打开文件夹
        Folder folder = store.getFolder(folderName);
        folder.open(Folder.READ_WRITE);

        // 保存连接
        storeMap.put(config.getId(), store);
        folderMap.put(config.getId(), folder);
        configMap.put(config.getId(), config);

        log.info("邮箱 {} 连接成功，开始监听文件夹: {}", config.getEmail(), folderName);

        // 添加消息监听器
        folder.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                Message[] messages = e.getMessages();
                for (Message message : messages) {
                    processNewMessage(message, config);
                }
            }
        });

        // 保持连接并轮询
        keepAlive(config.getId());
    }

    /**
     * 保持连接并轮询新邮件
     */
    private void keepAlive(Long configId) {
        EmailConfig config = configMap.get(configId);
        if (config == null) return;

        int intervalSeconds = config.getPollInterval() != null ? config.getPollInterval() : 30;

        while (storeMap.containsKey(configId)) {
            try {
                Folder folder = folderMap.get(configId);
                if (folder != null && folder.isOpen()) {
                    // 触发IDLE或轮询
                    folder.getMessageCount(); // 简单轮询
                }
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("邮箱 {} 轮询异常: {}", config.getEmail(), e.getMessage());
                // 尝试重连
                try {
                    reconnect(configId);
                } catch (Exception ex) {
                    log.error("邮箱 {} 重连失败: {}", config.getEmail(), ex.getMessage());
                    cleanup(configId);
                    break;
                }
            }
        }
    }

    /**
     * 处理新邮件
     */
    private void processNewMessage(Message message, EmailConfig config) {
        try {
            EmailMessage emailMessage = parseMessage(message, config);
            log.info("收到新邮件: {} -> {}", emailMessage.getFrom(), emailMessage.getSubject());

            // 调用处理器
            if (emailHandler != null) {
                emailHandler.handle(emailMessage);
            }
        } catch (Exception e) {
            log.error("处理邮件失败: {}", e.getMessage());
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
            builder.from(fromAddresses[0].toString());
            if (fromAddresses[0] instanceof InternetAddress) {
                builder.fromName(((InternetAddress) fromAddresses[0]).getPersonal());
            }
        }

        builder.subject(message.getSubject());

        // 收件人
        Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null) {
            List<String> toList = new ArrayList<>();
            for (Address addr : toAddresses) {
                toList.add(addr.toString());
            }
            builder.to(toList);
        }

        // 抄送
        Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
        if (ccAddresses != null) {
            List<String> ccList = new ArrayList<>();
            for (Address addr : ccAddresses) {
                ccList.add(addr.toString());
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
     * 重新连接
     */
    private void reconnect(Long configId) throws MessagingException {
        EmailConfig config = configMap.get(configId);
        if (config == null) return;

        cleanup(configId);
        connectAndListen(config);
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
    }

    /**
     * 停止所有监听
     */
    public void stopAllListeners() {
        for (Long configId : new ArrayList<>(storeMap.keySet())) {
            cleanup(configId);
        }
    }

    /**
     * 停止单个邮箱监听
     */
    public void stopListener(Long configId) {
        cleanup(configId);
        log.info("已停止邮箱监听: {}", configId);
    }

    /**
     * 重载邮箱配置
     */
    public void reloadListeners() {
        stopAllListeners();
        loadAndStartListeners();
    }

    /**
     * 获取监听状态
     */
    public Map<Long, String> getListenerStatus() {
        Map<Long, String> status = new HashMap<>();
        for (Map.Entry<Long, Store> entry : storeMap.entrySet()) {
            status.put(entry.getKey(), entry.getValue().isConnected() ? "已连接" : "未连接");
        }
        return status;
    }

    /**
     * 测试邮箱连接
     * 用于验证邮箱配置是否正确
     */
    public EmailTestResult testConnection(EmailConfig config) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("测试邮箱连接: {}", config.getEmail());

            String protocol = normalizeProtocol(config.getProtocol());
            String host = safeTrim(config.getHost());
            String email = safeTrim(config.getEmail());
            String password = safeTrim(config.getPassword());
            String folderName = normalizeFolder(config.getFolder());
            int port = config.getPort() != null ? config.getPort() : 993;

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

            // 创建Session
            Session session = Session.getInstance(props);
            session.setDebug(false);

            // 连接Store
            Store store = session.getStore(protocol);
            store.connect(host, email, password);

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
            return new EmailTestResult(false, "认证失败：邮箱地址或密码/授权码错误", duration, 0, e.getMessage());

        } catch (MessagingException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("邮箱 {} 连接失败: {}", config.getEmail(), e.getMessage());
            String errorMsg = e.getMessage();
            if (errorMsg.contains("connection") || errorMsg.contains("connect")) {
                errorMsg = "连接失败：无法连接到邮件服务器，请检查服务器地址和端口";
            } else if (errorMsg.contains("SSL") || errorMsg.contains("TLS")) {
                errorMsg = "SSL/TLS错误：请检查SSL配置是否正确";
            }
            return new EmailTestResult(false, errorMsg, duration, 0, e.getMessage());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("邮箱 {} 测试异常: {}", config.getEmail(), e.getMessage());
            return new EmailTestResult(false, "测试异常：" + e.getMessage(), duration, 0, e.getMessage());
        }
    }

    /**
     * 邮箱测试结果
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
    public interface EmailHandler {
        void handle(EmailMessage emailMessage);
    }

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

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    private String normalizeProtocol(String protocol) {
        String value = safeTrim(protocol);
        return (value == null || value.isEmpty()) ? "imap" : value.toLowerCase();
    }

    private String normalizeFolder(String folder) {
        String value = safeTrim(folder);
        return (value == null || value.isEmpty()) ? "INBOX" : value;
    }
}
