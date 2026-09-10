package com.example.demo.aiintegration.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Graph 邮件消费链路的 RabbitMQ 拓扑。
 *
 * <p>Java 和 Python Graph 使用同一组 durable exchange / queue / routing key，
 * 任一服务先启动都不会因为队列尚未声明而丢失邮件事件。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class AiRabbitConfiguration {

    /**
     * 创建 Graph Direct Exchange。
     *
     * @param properties Graph 集成配置
     * @return durable Direct Exchange
     */
    @Bean
    public DirectExchange graphExchange(AiIntegrationProperties properties) {
        return new DirectExchange(
                properties.getRabbitmq().getExchange(),
                true,
                false
        );
    }

    /**
     * 创建 Graph 邮件消费队列。
     *
     * @param properties Graph 集成配置
     * @return durable Queue
     */
    @Bean
    public Queue graphEmailQueue(AiIntegrationProperties properties) {
        return new Queue(properties.getRabbitmq().getQueue(), true);
    }

    /**
     * 绑定 Graph 邮件队列与 Direct Exchange。
     *
     * @param graphEmailQueue Graph 邮件队列
     * @param graphExchange Graph Direct Exchange
     * @param properties Graph 集成配置
     * @return RabbitMQ Binding
     */
    @Bean
    public Binding graphEmailBinding(
            Queue graphEmailQueue,
            DirectExchange graphExchange,
            AiIntegrationProperties properties) {
        return BindingBuilder.bind(graphEmailQueue)
                .to(graphExchange)
                .with(properties.getRabbitmq().getRoutingKey());
    }
}
