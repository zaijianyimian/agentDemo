package com.example.demo.dispatch.application.prompt;

/**
 * 模板拼接用的最小邮件元数据。
 */
public record EmailMetadata(String subject, String from, String bodyExcerpt) {

    public static EmailMetadata empty() {
        return new EmailMetadata("", "", "");
    }
}
