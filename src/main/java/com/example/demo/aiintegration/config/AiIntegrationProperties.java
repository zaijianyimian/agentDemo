package com.example.demo.aiintegration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 后端与 Python Graph 服务之间的集成配置。
 *
 * <p>MySQL 仍然作为 Java 业务主库；这里只配置 AI 专用 PostgreSQL 和 RabbitMQ 契约。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.graph")
public class AiIntegrationProperties {

    private boolean enabled = false;
    private final Postgres postgres = new Postgres();
    private final RabbitMq rabbitmq = new RabbitMq();

    /**
     * AI 专用 PostgreSQL 配置。
     */
    @Data
    public static class Postgres {
        private String jdbcUrl = "jdbc:postgresql://localhost:5432/agent_ai";
        private String username = "postgres";
        private String password = "postgres";
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private long connectionTimeoutMs = 5000L;
    }

    /**
     * Graph 邮件消费队列契约。
     */
    @Data
    public static class RabbitMq {
        private String exchange = "agent-messages";
        private String queue = "agent-messages-queue";
        private String routingKey = "agent-messages";
        private long confirmTimeoutMs = 5000L;
    }
}
