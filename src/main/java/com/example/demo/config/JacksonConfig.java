package com.example.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;

/**
 * Jackson 配置类 - 处理 LocalDateTime 序列化和 UTF-8 编码
 * 注意: ObjectMapper Bean 已在 AiConfiguration 中定义
 */
@Configuration
public class JacksonConfig {

    /**
     * Jackson2ObjectMapperBuilderCustomizer 用于 Spring Boot 自动配置
     * 作为 AiConfiguration 中 ObjectMapper 的补充配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 注册 Java 8 时间模块
            builder.modules(new JavaTimeModule());
            // 禁用将日期写为时间戳
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /**
     * 配置MappingJackson2HttpMessageConverter的字符集和ObjectMapper
     */
    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        return converter;
    }
}