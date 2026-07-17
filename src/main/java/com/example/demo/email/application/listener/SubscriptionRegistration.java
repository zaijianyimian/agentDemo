package com.example.demo.email.application.listener;

import java.time.LocalDateTime;

/**
 * provider Webhook 订阅注册结果。
 * 记录订阅 ID、过期时间、订阅资源以及订阅时返回的增量游标。
 */
public record SubscriptionRegistration(
        String subscriptionId,
        LocalDateTime expiresAt,
        String resource,
        String cursorType,
        String cursorValue
) {
}
