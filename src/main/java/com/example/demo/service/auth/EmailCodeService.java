package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.AuthEmailCode;
import com.example.demo.mapper.AuthEmailCodeMapper;
import com.example.demo.properties.AuthSecurityProperties;
import com.example.demo.service.email.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private final AuthEmailCodeMapper emailCodeMapper;
    private final EmailSenderService emailSenderService;
    private final AuthSecurityProperties securityProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    public int sendLoginCode(String email) {
        return sendCode(email, AuthConstants.PURPOSE_LOGIN, "AI Agent 登录验证码");
    }

    public int sendRegisterCode(String email) {
        return sendCode(email, AuthConstants.PURPOSE_REGISTER, "AI Agent 注册确认验证码");
    }

    private int sendCode(String email, String purpose, String subject) {
        LocalDateTime now = LocalDateTime.now();
        AuthEmailCode latest = findLatestByEmail(email, purpose);

        int cooldown = Math.max(0, securityProperties.getEmailCodeCooldownSeconds());
        if (latest != null && latest.getSendTime() != null) {
            long secondsSince = Duration.between(latest.getSendTime(), now).getSeconds();
            if (secondsSince < cooldown) {
                throw new IllegalArgumentException("验证码发送过于频繁，请 " + (cooldown - secondsSince) + " 秒后再试");
            }
        }

        String code = generateCode();
        AuthEmailCode emailCode = AuthEmailCode.builder()
                .email(email)
                .code(code)
                .purpose(purpose)
                .used(false)
                .sendTime(now)
                .expireTime(now.plusSeconds(Math.max(60, securityProperties.getEmailCodeTtlSeconds())))
                .build();
        emailCodeMapper.insert(emailCode);

        String html = buildCodeEmailHtml(subject, code, Math.max(1, securityProperties.getEmailCodeTtlSeconds() / 60));

        if (emailSenderService.isAvailable()) {
            try {
                emailSenderService.sendHtml(email, subject, html);
            } catch (Exception ex) {
                log.warn("验证码邮件发送失败，已记录验证码到日志用于开发调试: email={}, code={}", email, code, ex);
            }
        } else {
            log.warn("邮件服务不可用，开发环境验证码: email={}, code={}", email, code);
        }

        return cooldown;
    }

    public boolean verifyAndConsumeLoginCode(String email, String code) {
        LocalDateTime now = LocalDateTime.now();
        AuthEmailCode record = findLatestValidRecord(email, code, AuthConstants.PURPOSE_LOGIN);

        if (record == null || record.getExpireTime() == null || now.isAfter(record.getExpireTime())) {
            return false;
        }

        record.setUsed(true);
        emailCodeMapper.updateById(record);
        return true;
    }

    public boolean verifyAndConsumeAuthCode(String email, String code) {
        LocalDateTime now = LocalDateTime.now();
        AuthEmailCode record = findLatestValidRecord(email, code, AuthConstants.PURPOSE_LOGIN, AuthConstants.PURPOSE_REGISTER);

        if (record == null || record.getExpireTime() == null || now.isAfter(record.getExpireTime())) {
            return false;
        }

        record.setUsed(true);
        emailCodeMapper.updateById(record);
        return true;
    }

    private String generateCode() {
        int value = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(value);
    }

    private AuthEmailCode findLatestByEmail(String email, String purpose) {
        return emailCodeMapper.selectOne(
                new LambdaQueryWrapper<AuthEmailCode>()
                        .eq(AuthEmailCode::getEmail, email)
                        .eq(AuthEmailCode::getPurpose, purpose)
                        .orderByDesc(AuthEmailCode::getSendTime)
                        .last("LIMIT 1")
        );
    }

    private AuthEmailCode findLatestValidRecord(String email, String code, String... purposes) {
        return emailCodeMapper.selectOne(
                new LambdaQueryWrapper<AuthEmailCode>()
                        .eq(AuthEmailCode::getEmail, email)
                        .in(AuthEmailCode::getPurpose, (Object[]) purposes)
                        .eq(AuthEmailCode::getCode, code)
                        .eq(AuthEmailCode::getUsed, false)
                        .orderByDesc(AuthEmailCode::getSendTime)
                        .last("LIMIT 1")
        );
    }

    private String buildCodeEmailHtml(String subject, String code, int validMinutes) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            margin: 0;
                            padding: 0;
                            background: #FFF7ED;
                            color: #7C2D12;
                            font-family: 'Microsoft YaHei', Arial, sans-serif;
                        }
                        .container {
                            max-width: 620px;
                            margin: 0 auto;
                            padding: 24px;
                        }
                        .panel {
                            background: #FFFFFF;
                            border: 1px solid #FED7AA;
                            border-radius: 12px;
                            overflow: hidden;
                        }
                        .header {
                            padding: 16px 20px;
                            background: linear-gradient(135deg, #FDBA74 0%%, #F59E0B 100%%);
                            color: #7C2D12;
                            font-weight: 700;
                        }
                        .main {
                            padding: 20px;
                        }
                        .hint {
                            margin: 0 0 14px 0;
                            color: #9A3412;
                            line-height: 1.65;
                        }
                        .code-box {
                            margin: 8px 0 14px 0;
                            padding: 14px 16px;
                            text-align: center;
                            background: #FFFBEB;
                            border: 1px solid #FDE68A;
                            border-radius: 10px;
                            letter-spacing: 6px;
                            font-size: 28px;
                            font-weight: 700;
                            color: #B45309;
                        }
                        .disclaimer {
                            margin-top: 18px;
                            padding-top: 12px;
                            border-top: 1px solid #D1D5DB;
                            color: #6B7280;
                            font-size: 12px;
                            line-height: 1.6;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="panel">
                            <div class="header">%s</div>
                            <div class="main">
                                <p class="hint">您好，您的验证码如下，有效期 %d 分钟：</p>
                                <div class="code-box">%s</div>
                                <p class="hint">请勿将验证码泄露给他人。若非本人操作，请忽略本邮件。</p>
                                <div class="disclaimer">
                                    免责声明：本邮件由系统自动发送，仅用于身份验证与安全校验，不构成任何业务承诺。
                                </div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(subject, validMinutes, code);
    }
}
