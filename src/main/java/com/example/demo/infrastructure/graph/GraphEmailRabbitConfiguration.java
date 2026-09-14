package com.example.demo.infrastructure.graph;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Python Graph 邮件事件 RabbitMQ 基础设施配置。 */
@Configuration
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class GraphEmailRabbitConfiguration {

    /**
     * 声明 Java 发布、Python 消费的新邮件 DirectExchange。
     *
     * @param properties Graph 边界配置。
     * @return 持久化 DirectExchange。
     */
    @Bean
    public DirectExchange graphEmailExchange(GraphGatewayProperties properties) {
        return new DirectExchange(properties.getEmailExchange(), true, false);
    }
}
