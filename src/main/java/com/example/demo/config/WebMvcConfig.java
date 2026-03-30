package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WebMvc 配置类
 * 配置异步支持和 SSE 流式响应
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置 HTTP 消息转换器
     * 确保 String 类型响应使用 UTF-8 编码
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setSupportedMediaTypes(List.of(
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_HTML,
                MediaType.APPLICATION_JSON,
                new MediaType("text", "plain", StandardCharsets.UTF_8),
                new MediaType("application", "json", StandardCharsets.UTF_8)
        ));
        converters.add(0, stringConverter);
    }

    /**
     * 配置异步请求支持
     * 增加超时时间以支持长时间流式响应
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 设置异步请求超时时间（5分钟），支持长时间的流式响应
        configurer.setDefaultTimeout(300000);
    }

    /**
     * 配置内容协商
     * 确保 SSE 媒体类型正确处理
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.APPLICATION_JSON)
                .mediaType("text-event-stream", MediaType.TEXT_EVENT_STREAM)
                .mediaType("sse", MediaType.TEXT_EVENT_STREAM);
    }
}
