package com.example.demo.service.email;

import com.example.demo.properties.MailProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * SMTP邮件发送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final MailProperties mailProperties;
    private JavaMailSender mailSender;

    @PostConstruct
    public void init() {
        if (mailProperties.getSmtpHost() == null || mailProperties.getSmtpHost().isEmpty()) {
            log.warn("SMTP配置未设置，邮件发送功能将不可用");
            return;
        }

        if (mailProperties.getSmtpPassword() == null || mailProperties.getSmtpPassword().isEmpty()) {
            log.warn("SMTP密码未设置，邮件发送功能将不可用。请在application.yaml中配置app.mail.smtp-password");
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getSmtpHost());
        sender.setPort(mailProperties.getSmtpPort());
        sender.setUsername(mailProperties.getSmtpUsername());
        sender.setPassword(mailProperties.getSmtpPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        if (Boolean.TRUE.equals(mailProperties.getSmtpSslEnabled())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", mailProperties.getSmtpHost());
        }

        this.mailSender = sender;
        log.info("邮件发送服务初始化完成: {}", mailProperties.getSmtpUsername());
    }

    /**
     * 检查邮件发送服务是否可用
     */
    public boolean isAvailable() {
        return mailSender != null;
    }

    /**
     * 发送纯文本邮件
     */
    public void sendText(String to, String subject, String content) {
        sendEmail(to, subject, content, false);
    }

    /**
     * 发送HTML邮件
     */
    public void sendHtml(String to, String subject, String htmlContent) {
        sendEmail(to, subject, htmlContent, true);
    }

    /**
     * 发送邮件
     */
    private void sendEmail(String to, String subject, String content, boolean isHtml) {
        if (!isAvailable()) {
            log.warn("邮件发送服务不可用，跳过发送邮件: {}", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 设置发件人（带名称）
            String fromAddress = mailProperties.getSmtpUsername();
            String fromName = mailProperties.getFromName();
            try {
                helper.setFrom(fromAddress, fromName);
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(fromAddress);
            }

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);

            mailSender.send(message);
            log.info("邮件发送成功: {} -> {}", subject, to);

        } catch (MessagingException e) {
            log.error("邮件发送失败: {} -> {}, 错误: {}", subject, to, e.getMessage());
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送日程汇总邮件
     */
    public void sendScheduleSummary(String to, String date, String summaryContent) {
        String subject = "日程汇总 - " + date;
        String htmlContent = buildSummaryEmail(date, summaryContent);
        sendHtml(to, subject, htmlContent);
    }

    /**
     * 发送日程提醒邮件
     */
    public void sendScheduleReminder(String to, String date, String events) {
        String subject = "日程提醒 - " + date;
        String htmlContent = buildReminderEmail(date, events);
        sendHtml(to, subject, htmlContent);
    }

    /**
     * 构建汇总邮件HTML内容
     */
    private String buildSummaryEmail(String date, String summaryContent) {
        String body = """
                <h2>📅 日程汇总</h2>
                <p style="margin:0 0 16px 0;color:#9A3412;">%s</p>
                <div class="content-card">%s</div>
                """.formatted(date, summaryContent);
        return wrapEmailTemplate("日程汇总", body);
    }

    /**
     * 构建提醒邮件HTML内容
     */
    private String buildReminderEmail(String date, String events) {
        String body = """
                <h2>⏰ 日程提醒</h2>
                <p style="margin:0 0 16px 0;color:#9A3412;">%s</p>
                <div class="content-card">%s</div>
                """.formatted(date, events);
        return wrapEmailTemplate("日程提醒", body);
    }

    private String wrapEmailTemplate(String title, String body) {
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
                            line-height: 1.65;
                        }
                        .container {
                            max-width: 640px;
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
                            padding: 18px 20px;
                            background: linear-gradient(135deg, #FDBA74 0%%, #F59E0B 100%%);
                            color: #7C2D12;
                            font-weight: 700;
                            font-size: 16px;
                        }
                        .main {
                            padding: 20px;
                        }
                        h2 {
                            margin: 0 0 6px 0;
                            font-size: 20px;
                            color: #7C2D12;
                        }
                        .content-card {
                            background: #FFFBEB;
                            border: 1px solid #FDE68A;
                            border-radius: 10px;
                            padding: 14px 16px;
                            color: #7C2D12;
                        }
                        .disclaimer {
                            margin-top: 18px;
                            padding-top: 12px;
                            border-top: 1px solid #D1D5DB;
                            color: #6B7280;
                            font-size: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="panel">
                            <div class="header">AI Agent · %s</div>
                            <div class="main">
                                %s
                                <div class="disclaimer">
                                    免责声明：本邮件由系统自动发送，仅供通知与参考，不构成任何承诺或保证。如非本人操作，请忽略或联系管理员。
                                </div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(title, body);
    }
}
