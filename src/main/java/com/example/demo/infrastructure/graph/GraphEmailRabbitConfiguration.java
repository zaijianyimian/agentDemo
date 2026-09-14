package com.example.demo.infrastructure.graph;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
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

    /**
     * 声明 Python Graph 消费的新邮件持久化队列。
     *
     * @param properties Graph 边界配置。
     * @return 持久化队列。
     */
    @Bean
    public Queue graphEmailQueue(GraphGatewayProperties properties) {
        return new Queue(properties.getEmailQueue(), true);
    }

    /**
     * 将新邮件队列绑定到 Graph 邮件交换机。
     *
     * @param graphEmailQueue Graph 邮件队列。
     * @param graphEmailExchange Graph 邮件交换机。
     * @param properties Graph 边界配置。
     * @return 队列绑定关系。
     */
    @Bean
    public Binding graphEmailBinding(
            @Qualifier("graphEmailQueue") Queue graphEmailQueue,
            @Qualifier("graphEmailExchange") DirectExchange graphEmailExchange,
            GraphGatewayProperties properties) {
        return BindingBuilder.bind(graphEmailQueue)
                .to(graphEmailExchange)
                .with(properties.getEmailRoutingKey());
    }
}
