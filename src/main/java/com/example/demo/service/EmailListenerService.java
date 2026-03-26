package com.example.demo.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邮件监听服务
 * 启动时从数据库读取邮箱配置，监听新邮件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailListenerService {

    private final EmailConfigMapper emailConfigMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // 存储每个邮箱的Store和Folder连接
    private final Map<Long, Store> storeMap = new ConcurrentHashMap<>();
    private final Map<Long, Folder> folderMap = new ConcurrentHashMap<>();
    private final Map<Long, EmailConfig> configMap = new ConcurrentHashMap<>();

    // 邮件处理器（可以注入自定义处理器）
    private EmailHandler emailHandler;

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

        // 配置邮件属性
        Properties props = new Properties();
        props.put("mail.store.protocol", config.getProtocol());
        props.put("mail." + config.getProtocol() + ".host", config.getHost());
        props.put("mail." + config.getProtocol() + ".port", config.getPort());
        props.put("mail." + config.getProtocol() + ".connectiontimeout", 10000);
        props.put("mail." + config.getProtocol() + ".timeout", 10000);

        if (Boolean.TRUE.equals(config.getSslEnabled())) {
            props.put("mail." + config.getProtocol() + ".ssl.enable", "true");
            props.put("mail." + config.getProtocol() + ".ssl.trust", config.getHost());
        }

        // 创建Session
        Session session = Session.getInstance(props);
        session.setDebug(false);

        // 连接Store
        Store store = session.getStore(config.getProtocol());
        store.connect(config.getHost(), config.getEmail(), config.getPassword());

        // 打开文件夹
        String folderName = config.getFolder() != null ? config.getFolder() : "INBOX";
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
     * 邮件处理器接口
     */
    public interface EmailHandler {
        void handle(EmailMessage emailMessage);
    }
}