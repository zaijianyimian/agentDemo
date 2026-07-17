package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
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
