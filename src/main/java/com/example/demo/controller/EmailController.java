package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import com.example.demo.service.email.EmailAuthConfigService;
import com.example.demo.service.email.EmailListenerService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件管理控制器
 */
@RestController
@RequestMapping("/api/email")
public class EmailController {

    @Resource
    private EmailConfigMapper emailConfigMapper;

    @Resource
    private EmailListenerService emailListenerService;

    @Resource
    private EmailAuthConfigService emailAuthConfigService;

    // ==================== 邮箱配置管理 ====================

    /**
     * 获取所有邮箱配置
     */
    @GetMapping("/config/list")
    public List<EmailConfig> listConfigs() {
        List<EmailConfig> list = emailConfigMapper.selectList(null);
        list.forEach(config -> {
            normalizeConfig(config);
            emailAuthConfigService.sanitizeForResponse(config);
        });
        return list;
    }

    /**
     * 获取启用的邮箱配置
     */
    @GetMapping("/config/enabled")
    public List<EmailConfig> listEnabledConfigs() {
        List<EmailConfig> list = emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>().eq(EmailConfig::getEnabled, true)
        );
        list.forEach(config -> {
            normalizeConfig(config);
            emailAuthConfigService.sanitizeForResponse(config);
        });
        return list;
    }

    /**
     * 根据ID获取邮箱配置
     */
    @GetMapping("/config/{id}")
    public EmailConfig getConfig(@PathVariable Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        normalizeConfig(config);
        emailAuthConfigService.sanitizeForResponse(config);
        return config;
    }

    /**
     * 添加邮箱配置
     */
    @PostMapping("/config")
    public ResponseEntity<String> addConfig(@RequestBody EmailConfig config) {
        normalizeConfig(config);
        if (config == null) {
            return ResponseEntity.badRequest().body("请求体不能为空");
        }
        if (isBlank(config.getEmail()) || isBlank(config.getHost())) {
            return ResponseEntity.badRequest().body("邮箱地址和服务器不能为空");
        }
        String authType = config.getAuthType();
        boolean oauthMode = EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(authType)
                || EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(authType);
        if (!oauthMode && isBlank(config.getPassword())) {
            return ResponseEntity.badRequest().body("密码/授权码不能为空");
        }
        String authValidationError = validateAuthConfig(config, null);
        if (authValidationError != null) {
            return ResponseEntity.badRequest().body(authValidationError);
        }
        // 设置默认值
        if (config.getProtocol() == null) {
            config.setProtocol("imap");
        }
        if (config.getPort() == null) {
            config.setPort(993);
        }
        if (config.getSslEnabled() == null) {
            config.setSslEnabled(true);
        }
        if (config.getEnabled() == null) {
            config.setEnabled(false);
        }
        if (config.getFolder() == null) {
            config.setFolder("INBOX");
        }
        if (config.getPollInterval() == null) {
            config.setPollInterval(30);
        }
        emailAuthConfigService.prepareForPersist(config, null);

        emailConfigMapper.insert(config);
        return ResponseEntity.ok("添加成功");
    }

    /**
     * 更新邮箱配置
     */
    @PutMapping("/config")
    public ResponseEntity<String> updateConfig(@RequestBody EmailConfig config) {
        if (config == null || config.getId() == null) {
            return ResponseEntity.badRequest().body("邮箱配置ID不能为空");
        }
        EmailConfig existing = emailConfigMapper.selectById(config.getId());
        if (existing == null) {
            return ResponseEntity.badRequest().body("邮箱配置不存在");
        }
        normalizeConfig(existing);
        emailAuthConfigService.decodeTransientFields(existing);
        normalizeConfig(config);
        if (isBlank(config.getEmail()) || isBlank(config.getHost())) {
            return ResponseEntity.badRequest().body("邮箱地址和服务器不能为空");
        }
        String authType = config.getAuthType();
        boolean oauthMode = EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(authType)
                || EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(authType);
        if (!oauthMode && isBlank(config.getPassword())) {
            config.setPassword(existing.getPassword());
        }
        if (config.getProtocol() == null) {
            config.setProtocol(existing.getProtocol());
        }
        if (config.getPort() == null) {
            config.setPort(existing.getPort());
        }
        if (config.getSslEnabled() == null) {
            config.setSslEnabled(existing.getSslEnabled());
        }
        if (config.getFolder() == null) {
            config.setFolder(existing.getFolder());
        }
        if (config.getPollInterval() == null) {
            config.setPollInterval(existing.getPollInterval());
        }
        if (config.getEnabled() == null) {
            config.setEnabled(existing.getEnabled());
        }
        String authValidationError = validateAuthConfig(config, existing);
        if (authValidationError != null) {
            return ResponseEntity.badRequest().body(authValidationError);
        }
        emailAuthConfigService.prepareForPersist(config, existing);
        emailConfigMapper.updateById(config);
        return ResponseEntity.ok("更新成功");
    }

    /**
     * 删除邮箱配置
     */
    @DeleteMapping("/config/{id}")
    public ResponseEntity<String> deleteConfig(@PathVariable Long id) {
        // 先停止监听
        emailListenerService.stopListener(id);
        // 再删除配置
        emailConfigMapper.deleteById(id);
        return ResponseEntity.ok("删除成功");
    }

    // ==================== 监听管理 ====================

    /**
     * 启动邮箱监听
     */
    @PostMapping("/listener/start/{id}")
    public ResponseEntity<String> startListener(@PathVariable Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        if (config == null) {
            return ResponseEntity.badRequest().body("邮箱配置不存在");
        }
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);
        EmailListenerService.EmailTestResult testResult = emailListenerService.testConnection(config);
        if (!testResult.isSuccess()) {
            return ResponseEntity.badRequest().body(testResult.getMessage());
        }

        // 更新启用状态
        config.setEnabled(true);
        emailConfigMapper.updateById(config);

        // 启动监听
        emailListenerService.startListener(config);
        return ResponseEntity.ok("已启动监听: " + config.getEmail());
    }

    /**
     * 停止邮箱监听
     */
    @PostMapping("/listener/stop/{id}")
    public ResponseEntity<String> stopListener(@PathVariable Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        if (config == null) {
            return ResponseEntity.badRequest().body("邮箱配置不存在");
        }
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);

        // 更新启用状态
        config.setEnabled(false);
        emailConfigMapper.updateById(config);

        // 停止监听
        emailListenerService.stopListener(id);
        return ResponseEntity.ok("已停止监听: " + config.getEmail());
    }

    /**
     * 重载所有邮箱监听
     */
    @PostMapping("/listener/reload")
    public ResponseEntity<String> reloadListeners() {
        emailListenerService.reloadListeners();
        return ResponseEntity.ok("已重载所有邮箱监听");
    }

    /**
     * 获取监听状态
     */
    @GetMapping("/listener/status")
    public Map<Long, String> getListenerStatus() {
        return emailListenerService.getListenerStatus();
    }

    // ==================== 邮箱测试 ====================

    /**
     * 测试邮箱连接
     */
    @PostMapping("/config/{id}/test")
    public ResponseEntity<Map<String, Object>> testConfig(@PathVariable Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "邮箱配置不存在"
            ));
        }
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);

        EmailListenerService.EmailTestResult result = emailListenerService.testConnection(config);
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "durationMs", result.getDurationMs(),
                "messageCount", result.getMessageCount(),
                "errorDetail", result.getErrorDetail() != null ? result.getErrorDetail() : ""
        ));
    }

    /**
     * 检查已保存邮箱配置的网络连通性（服务器 -> 邮件服务器）
     */
    @GetMapping("/config/{id}/network-check")
    public ResponseEntity<Map<String, Object>> checkNetwork(@PathVariable Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "邮箱配置不存在"
            ));
        }
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);
        EmailListenerService.NetworkCheckResult result = emailListenerService.checkNetworkConnectivity(
                config.getHost(),
                config.getPort() == null ? 993 : config.getPort(),
                10000
        );
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "durationMs", result.getDurationMs(),
                "resolvedIp", result.getResolvedIp() != null ? result.getResolvedIp() : "",
                "errorDetail", result.getErrorDetail() != null ? result.getErrorDetail() : ""
        ));
    }

    /**
     * 检查未保存配置的网络连通性
     */
    @PostMapping("/config/network-check")
    public ResponseEntity<Map<String, Object>> checkNewConfigNetwork(@RequestBody EmailConfig config) {
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);
        EmailListenerService.NetworkCheckResult result = emailListenerService.checkNetworkConnectivity(
                config.getHost(),
                config.getPort() == null ? 993 : config.getPort(),
                10000
        );
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "durationMs", result.getDurationMs(),
                "resolvedIp", result.getResolvedIp() != null ? result.getResolvedIp() : "",
                "errorDetail", result.getErrorDetail() != null ? result.getErrorDetail() : ""
        ));
    }

    /**
     * 测试新邮箱配置（未保存的配置）
     */
    @PostMapping("/config/test")
    public ResponseEntity<Map<String, Object>> testNewConfig(@RequestBody EmailConfig config) {
        normalizeConfig(config);
        emailAuthConfigService.decodeTransientFields(config);
        EmailListenerService.EmailTestResult result = emailListenerService.testConnection(config);
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "durationMs", result.getDurationMs(),
                "messageCount", result.getMessageCount(),
                "errorDetail", result.getErrorDetail() != null ? result.getErrorDetail() : ""
        ));
    }

    // ==================== 常用邮箱模板 ====================

    /**
     * 获取常用邮箱服务器配置
     */
    @GetMapping("/templates")
    public List<EmailTemplate> getEmailTemplates() {
        return List.of(
                new EmailTemplate("QQ邮箱", "imap.qq.com", 993, true, "imap"),
                new EmailTemplate("163邮箱", "imap.163.com", 993, true, "imap"),
                new EmailTemplate("126邮箱", "imap.126.com", 993, true, "imap"),
                new EmailTemplate("Gmail", "imap.gmail.com", 993, true, "imap"),
                new EmailTemplate("Outlook", "outlook.office365.com", 993, true, "imap"),
                new EmailTemplate("阿里企业邮箱", "imap.qiye.aliyun.com", 993, true, "imap"),
                new EmailTemplate("腾讯企业邮箱", "imap.exmail.qq.com", 993, true, "imap")
        );
    }

    /**
     * 邮箱服务器模板
     */
    public record EmailTemplate(String name, String host, int port, boolean sslEnabled, String protocol) {}

    private void normalizeConfig(EmailConfig config) {
        if (config == null) {
            return;
        }
        if (config.getEmail() != null) {
            config.setEmail(config.getEmail().trim());
        }
        if (config.getHost() != null) {
            config.setHost(config.getHost().trim());
        }
        if (config.getPassword() != null) {
            config.setPassword(config.getPassword().trim());
        }
        if (config.getProtocol() != null) {
            config.setProtocol(config.getProtocol().trim().toLowerCase());
        }
        if (config.getFolder() != null) {
            config.setFolder(config.getFolder().trim());
        }
        if (config.getRemark() != null) {
            config.setRemark(config.getRemark().trim());
        }
        if (config.getAuthType() != null) {
            config.setAuthType(config.getAuthType().trim());
        }
        if (config.getOauthClientId() != null) {
            config.setOauthClientId(config.getOauthClientId().trim());
        }
        if (config.getOauthClientSecret() != null) {
            config.setOauthClientSecret(config.getOauthClientSecret().trim());
        }
        if (config.getOauthRefreshToken() != null) {
            config.setOauthRefreshToken(config.getOauthRefreshToken().trim());
        }
        if (config.getOauthAccessToken() != null) {
            config.setOauthAccessToken(config.getOauthAccessToken().trim());
        }
        if (config.getOauthTokenEndpoint() != null) {
            config.setOauthTokenEndpoint(config.getOauthTokenEndpoint().trim());
        }
        if (config.getOauthScope() != null) {
            config.setOauthScope(config.getOauthScope().trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String validateAuthConfig(EmailConfig incoming, EmailConfig existing) {
        String authType = incoming == null ? null : incoming.getAuthType();
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_ACCESS_TOKEN.equalsIgnoreCase(authType)) {
            String accessToken = firstNonBlank(
                    incoming == null ? null : incoming.getOauthAccessToken(),
                    existing == null ? null : existing.getOauthAccessToken()
            );
            if (isBlank(accessToken)) {
                return "OAuth2 Access Token 模式下 access token 不能为空";
            }
        }
        if (EmailAuthConfigService.AUTH_TYPE_OAUTH2_REFRESH_TOKEN.equalsIgnoreCase(authType)) {
            String clientId = firstNonBlank(
                    incoming == null ? null : incoming.getOauthClientId(),
                    existing == null ? null : existing.getOauthClientId()
            );
            String refreshToken = firstNonBlank(
                    incoming == null ? null : incoming.getOauthRefreshToken(),
                    existing == null ? null : existing.getOauthRefreshToken()
            );
            if (isBlank(clientId)) {
                return "OAuth2 Refresh Token 模式下 clientId 不能为空";
            }
            if (isBlank(refreshToken)) {
                return "OAuth2 Refresh Token 模式下 refresh token 不能为空";
            }
        }
        return null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (!isBlank(preferred)) {
            return preferred;
        }
        return isBlank(fallback) ? null : fallback;
    }
}
