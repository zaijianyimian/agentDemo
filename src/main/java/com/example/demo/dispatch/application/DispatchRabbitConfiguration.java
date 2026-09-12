package com.example.demo.dispatch.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 执行请求队列与 JSON 消息转换配置。 */
@Configuration
public class DispatchRabbitConfiguration {

    /** 声明 Python 发布、Java 消费的持久化执行请求队列。 */
    @Bean
    public Queue dispatchExecutionQueue(DispatchProperties properties) {
        return new Queue(properties.getExecutionQueue(), true);
    }

    /** 声明 Java 发布、Python 消费的持久化执行结果队列。 */
    @Bean
    public Queue dispatchResultQueue(DispatchProperties properties) {
        return new Queue(properties.getResultQueue(), true);
    }

    /** 使用项目统一 ObjectMapper 反序列化 execution request。 */
    @Bean
    public MessageConverter dispatchMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
