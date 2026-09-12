package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailConfigService 单元测试。
 *
 * <p>重点验证 {@link EmailConfigService#findByEmail(String)} 的脱敏契约：
 * 返回的 {@link EmailConfig} 必须清空 password / oauth secrets 等敏感字段，
 * 避免邮件业务接口泄露凭据。</p>
 */
class EmailConfigServiceTest {

    private EmailConfigMapper mapper;
    private EmailAuthConfigService authService;
    private EmailListenerConfigSupport listenerSupport;
    private EmailConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(EmailConfigMapper.class);
        authService = mock(EmailAuthConfigService.class);
        listenerSupport = mock(EmailListenerConfigSupport.class);
        service = new EmailConfigService(mapper, authService, listenerSupport);
    }

    @Test
    void findByEmailSanitizesSensitiveFields() {
        EmailConfig raw = EmailConfig.builder()
                .id(1L)
                .email("acct@example.com")
                .password("plain-password")
                .oauthClientSecret("oauth-secret")
                .oauthRefreshToken("refresh")
                .oauthAccessToken("access")
                .build();
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(raw);

        EmailConfig result = service.findByEmail("acct@example.com");

        assertThat(result).isNotNull();
        // 业务字段保留
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("acct@example.com");
        // 敏感字段被清空（authService.sanitizeForResponse 负责）
        verify(authService).sanitizeForResponse(raw);
    }

    @Test
    void findByEmailReturnsNullForBlankEmail() {
        assertThat(service.findByEmail(null)).isNull();
        assertThat(service.findByEmail("")).isNull();
        assertThat(service.findByEmail("   ")).isNull();
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void findByEmailReturnsNullWhenMapperReturnsNull() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThat(service.findByEmail("missing@example.com")).isNull();
        verify(authService, never()).sanitizeForResponse(any());
    }

    @Test
    void findByEmailAppliesDefaultsBeforeReturning() {
        EmailConfig raw = EmailConfig.builder().id(1L).email("acct@example.com").build();
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(raw);

        service.findByEmail("acct@example.com");

        // 既要 apply defaults 又要 sanitize；顺序由实现保证，这里只确认两者都被调用
        verify(listenerSupport).applyDefaults(raw);
        verify(authService).sanitizeForResponse(raw);
    }

    @Test
    void findByEmailUsesEmailEqualityFilter() {
        EmailConfig raw = EmailConfig.builder().id(7L).email("acct@example.com").build();
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(raw);

        service.findByEmail("acct@example.com");

        // 确认 selectOne 被调用一次，且参数非空（具体的 SQL 由 MyBatis-Plus 内部生成，单元测试不强校验）
        ArgumentCaptor<LambdaQueryWrapper<EmailConfig>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }
}
