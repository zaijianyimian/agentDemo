package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import com.example.demo.service.EmailListenerService;
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

    // ==================== 邮箱配置管理 ====================

    /**
     * 获取所有邮箱配置
     */
    @GetMapping("/config/list")
    public List<EmailConfig> listConfigs() {
        return emailConfigMapper.selectList(null);
    }

    /**
     * 获取启用的邮箱配置
     */
    @GetMapping("/config/enabled")
    public List<EmailConfig> listEnabledConfigs() {
        return emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>().eq(EmailConfig::getEnabled, true)
        );
    }

    /**
     * 根据ID获取邮箱配置
     */
    @GetMapping("/config/{id}")
    public EmailConfig getConfig(@PathVariable Long id) {
        return emailConfigMapper.selectById(id);
    }

    /**
     * 添加邮箱配置
     */
    @PostMapping("/config")
    public ResponseEntity<String> addConfig(@RequestBody EmailConfig config) {
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

        emailConfigMapper.insert(config);
        return ResponseEntity.ok("添加成功");
    }

    /**
     * 更新邮箱配置
     */
    @PutMapping("/config")
    public ResponseEntity<String> updateConfig(@RequestBody EmailConfig config) {
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
}