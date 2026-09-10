package com.example.demo.aiintegration.messaging;

import com.example.demo.aiintegration.config.AiIntegrationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 向 Python Graph 发布 Agent 请求消息。
 *
 * <p>当前与 graph 项目保持最小契约，只发送 {@code email_id}，邮件正文由 Graph 自行从 PostgreSQL 加载。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class GraphMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AiIntegrationProperties properties;

    /**
     * 创建 Graph MQ 发布器。
     *
     * @param rabbitTemplate RabbitMQ 模板
     * @param objectMapper JSON 序列化器
     * @param properties Graph 集成配置
     */
    public GraphMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AiIntegrationProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 发布邮件 Agent 请求，并等待 Broker Publisher Confirm。
     *
     * @param emailId AI PostgreSQL 中的邮件 ID
     */
    public void publishEmail(long emailId) {
        String payload = serializePayload(emailId);
        CorrelationData correlationData = new CorrelationData("email-" + emailId);
        AiIntegrationProperties.RabbitMq rabbitmq = properties.getRabbitmq();

        rabbitTemplate.convertAndSend(
                rabbitmq.getExchange(),
                rabbitmq.getRoutingKey(),
                payload,
                correlationData
        );

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    rabbitmq.getConfirmTimeoutMs(),
                    TimeUnit.MILLISECONDS
            );
            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ 未确认 Graph 邮件消息: " + confirm.getReason()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ Confirm 被中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("等待 RabbitMQ Confirm 失败", exception);
        }
    }

    private String serializePayload(long emailId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "email_id",
                    String.valueOf(emailId)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 Graph 邮件消息失败", exception);
        }
    }
}
