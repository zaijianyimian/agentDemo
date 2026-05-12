package com.example.demo.service.email;

import com.example.demo.entity.EmailConfig;
import com.example.demo.service.listener.ListenerConnectionTester;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 邮箱连接测试服务。
 *
 * 集中管理 IMAP/POP3 网络探测、认证参数、OAuth2 token 刷新和厂商兼容属性，
 * 便于后续扩展更多认证方式或诊断步骤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailConnectionTestService implements ListenerConnectionTester<EmailConfig, EmailConnectionTestService.EmailTestResult> {

    private final EmailAuthConfigService emailAuthConfigService;
    private final ObjectMapper objectMapper;

    /**
     * 测试邮箱连接，用于验证配置是否可以真实登录并打开目标文件夹。
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
            AuthCredential authCredential = resolveAuthCredential(config, host);

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

            Properties props = buildMailProperties(config, protocol, host, port, authCredential);
            Session session = Session.getInstance(props);
            session.setDebug(false);

            Store store = session.getStore(protocol);
            store.connect(host, email, authCredential.secret());

            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            int messageCount = folder.getMessageCount();

            folder.close(false);
            store.close();

            long duration = System.currentTimeMillis() - startTime;
            log.info("邮箱 {} 连接测试成功，耗时 {}ms，文件夹 {} 共有 {} 封邮件",
                    config.getEmail(), duration, folderName, messageCount);
            return new EmailTestResult(true, "连接成功", duration, messageCount, null);
        } catch (AuthenticationFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("邮箱 {} 认证失败: {}", safeEmail(config), e.getMessage());
            return new EmailTestResult(false, normalizeAuthError(config, e.getMessage()), duration, 0, e.getMessage());
        } catch (MessagingException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("邮箱 {} 连接失败: {}", safeEmail(config), e.getMessage());
            return new EmailTestResult(false, normalizeMessagingError(e.getMessage()), duration, 0, e.getMessage());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("邮箱 {} 测试异常: {}", safeEmail(config), e.getMessage());
            return new EmailTestResult(false, "测试异常：" + e.getMessage(), duration, 0, e.getMessage());
        }
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
            return new NetworkCheckResult(
                    true,
                    "网络连通",
                    System.currentTimeMillis() - start,
                    address.getHostAddress(),
                    null
            );
        } catch (Exception e) {
            return new NetworkCheckResult(
                    false,
                    "网络不通",
                    System.currentTimeMillis() - start,
                    null,
                    e.getMessage()
            );
        }
    }

    public Properties buildMailProperties(EmailConfig config, String protocol, String host, int port, AuthCredential authCredential) {
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
        return props;
    }

    public void applyAuthProperties(Properties props, String protocol, AuthCredential authCredential) {
        String prefix = "mail." + protocol + ".";

        if (authCredential != null && authCredential.mode() == AuthMode.OAUTH2) {
            props.put(prefix + "auth.mechanisms", "XOAUTH2");
            props.put(prefix + "auth.login.disable", "true");
            props.put(prefix + "auth.plain.disable", "true");
        } else {
            props.put(prefix + "auth.login.disable", "false");
            props.put(prefix + "auth.plain.disable", "false");
        }
    }

    public void applyVendorSpecificProperties(Properties props, String protocol, String host) {
        if (!StringUtils.hasText(host)) {
            return;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        String prefix = "mail." + protocol + ".";

        if (lowerHost.contains("163.com") || lowerHost.contains("126.com")
                || lowerHost.contains("188.com") || lowerHost.contains("yeah.net")) {
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            props.put(prefix + "ssl.trust", "*");
            props.put(prefix + "usesocketchannels", "true");
            props.put(prefix + "sasl.enable", "false");
            props.put(prefix + "peek", "true");
            props.put(prefix + "connectionpool.debug", "false");
        }

        if (lowerHost.contains("qq.com") || lowerHost.contains("exmail.qq.com")) {
            props.put(prefix + "ssl.enable", "true");
            props.put(prefix + "starttls.enable", "false");
            props.put(prefix + "ssl.trust", "*");
            props.put(prefix + "connectiontimeout", 15000);
            props.put(prefix + "timeout", 15000);
        }
    }

    public AuthCredential resolveAuthCredential(EmailConfig config, String host) {
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

    public String normalizeProtocol(String protocol) {
        String value = safeTrim(protocol);
        return (value == null || value.isEmpty()) ? "imap" : value.toLowerCase();
    }

    public String normalizeFolder(String folder) {
        String value = safeTrim(folder);
        return (value == null || value.isEmpty()) ? "INBOX" : value;
    }

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
            HttpURLConnection connection = (HttpURLConnection) new URL(tokenEndpoint).openConnection();
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

    private String normalizeAuthError(EmailConfig config, String detail) {
        String errorMsg = detail;
        if (errorMsg == null) {
            return "认证失败";
        }
        if (errorMsg.contains("Unsafe Login") || errorMsg.contains("LOGIN")) {
            return "认证失败：163/126邮箱需要使用授权码而非登录密码。请在邮箱设置中开启IMAP服务并生成授权码";
        }
        if (errorMsg.contains("Too many login")) {
            return "登录频率限制：请稍后再试或检查是否有其他客户端在同时登录";
        }
        if (errorMsg.contains("Invalid credentials")) {
            if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(config.getAuthType())
                    || EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(config.getAuthType())) {
                return "认证失败：OAuth2令牌无效或过期，请更新 access token / refresh token";
            }
            return "认证失败：邮箱地址或密码/授权码错误";
        }
        return errorMsg;
    }

    private String normalizeMessagingError(String detail) {
        String errorMsg = detail;
        if (errorMsg == null) {
            return "邮件服务连接失败";
        }
        if (errorMsg.contains("Unsafe Login")) {
            return "不安全登录被拒绝：163/126邮箱需要使用授权码而非登录密码。请在邮箱网页版设置中：1.开启IMAP服务 2.生成客户端授权码";
        }
        if (errorMsg.contains("connection") || errorMsg.contains("connect")) {
            return "连接失败：无法连接到邮件服务器，请检查服务器地址和端口";
        }
        if (errorMsg.contains("SSL") || errorMsg.contains("TLS")) {
            return "SSL/TLS错误：请检查SSL配置是否正确";
        }
        if (errorMsg.contains("timed out") || errorMsg.contains("timeout")) {
            return "连接超时：服务器无法访问邮件服务器，请检查网络或防火墙设置";
        }
        return errorMsg;
    }

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

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    private String safeEmail(EmailConfig config) {
        return config == null ? "unknown" : config.getEmail();
    }

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

    public enum AuthMode {
        PASSWORD,
        OAUTH2
    }

    public record AuthCredential(AuthMode mode, String secret) {
    }
}
