package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 邮箱配置查询服务。
 * 统一在读取时补默认值并脱敏，供内部模块（非 Controller）使用。
 */
@Service
@RequiredArgsConstructor
public class EmailConfigService {

    private final EmailConfigMapper emailConfigMapper;
    private final EmailAuthConfigService emailAuthConfigService;
    private final EmailListenerConfigSupport emailListenerConfigSupport;
    private final EmailListenerService emailListenerService;
    private final CurrentUserContext currentUser;

    /**
     * 查询全部邮箱配置，补全默认值并清空敏感字段后返回。
     */
    public List<EmailConfig> listAll() {
        List<EmailConfig> configs = emailConfigMapper.selectList(null);
        configs.forEach(config -> {
            emailListenerConfigSupport.applyDefaults(config);
            emailAuthConfigService.sanitizeForResponse(config);
        });
        return configs;
    }

    public List<EmailConfig> listEnabled() {
        List<EmailConfig> configs = emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>().eq(EmailConfig::getEnabled, true));
        configs.forEach(config -> {
            emailListenerConfigSupport.applyDefaults(config);
            emailAuthConfigService.sanitizeForResponse(config);
        });
        return configs;
    }

    /** Scoped lookup performed before callers may decode credentials or connect to a provider. */
    public EmailConfig requireOwned(Long id) {
        EmailConfig config = emailConfigMapper.selectById(id);
        if (config == null) {
            throw new UserResourceNotFoundException("邮箱配置不存在");
        }
        return config;
    }

    public EmailConfig findOwned(Long id) {
        return emailConfigMapper.selectById(id);
    }

    public void create(EmailConfig config) {
        config.setUserId(currentUser.requireUserId());
        emailConfigMapper.insert(config);
    }

    public void update(EmailConfig config) {
        EmailConfig existing = requireOwned(config.getId());
        config.setUserId(existing.getUserId());
        emailConfigMapper.updateById(config);
    }

    public void delete(Long id) {
        requireOwned(id);
        emailConfigMapper.deleteById(id);
    }

    public EmailListenerService.EmailTestResult testOwnedConnection(Long id) {
        EmailConfig config = requireOwned(id);
        emailListenerConfigSupport.applyDefaults(config);
        emailAuthConfigService.decodeTransientFields(config);
        return emailListenerService.testConnection(config);
    }

    public EmailListenerService.NetworkCheckResult checkOwnedNetwork(Long id) {
        EmailConfig config = requireOwned(id);
        emailListenerConfigSupport.applyDefaults(config);
        emailAuthConfigService.decodeTransientFields(config);
        return emailListenerService.checkNetworkConnectivity(
                config.getHost(), config.getPort() == null ? 993 : config.getPort(), 10000);
    }

    /**
     * 按邮箱地址精确查找配置，补全默认值并清空敏感字段后返回。
     */
    public EmailConfig findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        EmailConfig config = emailConfigMapper.selectOne(
                new LambdaQueryWrapper<EmailConfig>().eq(EmailConfig::getEmail, email));
        if (config == null) {
            return null;
        }
        emailListenerConfigSupport.applyDefaults(config);
        emailAuthConfigService.sanitizeForResponse(config);
        return config;
    }
}
