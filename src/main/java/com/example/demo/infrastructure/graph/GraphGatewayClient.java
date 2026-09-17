package com.example.demo.infrastructure.graph;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelOption;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Python Graph 内部 HTTP/SSE 客户端。
 *
 * <p>交互式 Agent 请求通过本客户端访问 Python；新邮件不经过 HTTP，而由
 * {@link GraphEmailRabbitConfiguration} 对应的 RabbitMQ 边界异步投递。</p>
 */
@Component
public class GraphGatewayClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final GraphGatewayProperties properties;
    private final WebClient webClient;

    public GraphGatewayClient(GraphGatewayProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        int connectTimeoutMillis = Math.toIntExact(
                Duration.ofSeconds(properties.getConnectTimeoutSeconds()).toMillis());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 调用 Python Graph 完成一次非流式 Agent 对话。
     *
     * @param userId 当前用户 ID。
     * @param sessionId UUID 会话 ID；旧兼容入口可为空。
     * @param message 用户消息。
     * @return Agent 最终文本。
     */
    public String chat(long userId, String sessionId, String message) {
        GraphChatResponse response = webClient.post()
                .uri("/internal/chat/complete")
                .headers(headers -> applyUserContextHeader(headers, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new GraphChatRequest(userId, sessionId, message))
                .retrieve()
                .bodyToMono(GraphChatResponse.class)
                .block(Duration.ofSeconds(properties.getResponseTimeoutSeconds()));
        if (response == null || response.content() == null) {
            throw new IllegalStateException("Graph 未返回有效聊天内容");
        }
        return response.content();
    }

    /**
     * 调用 Python Graph 的 SSE Agent 对话接口。
     *
     * @param userId 当前用户 ID。
     * @param sessionId UUID 会话 ID；旧兼容入口可为空。
     * @param message 用户消息。
     * @return Agent 文本数据流，不包含 Python 的结束标记。
     */
    public Flux<String> streamChat(long userId, String sessionId, String message) {
        return webClient.post()
                .uri("/internal/chat/stream")
                .headers(headers -> applyUserContextHeader(headers, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new GraphChatRequest(userId, sessionId, message))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .handle((event, sink) -> {
                    String data = event.data();
                    if (data != null && !data.isBlank() && !"[DONE]".equals(data)) {
                        sink.next(data);
                    }
                });
    }

    private void applyUserContextHeader(org.springframework.http.HttpHeaders headers, long userId) {
        headers.set(USER_ID_HEADER, String.valueOf(userId));
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Java 到 Python 的聊天请求。 */
    public record GraphChatRequest(
            @JsonProperty("user_id") long userId,
            @JsonProperty("session_id") String sessionId,
            String message) {
    }

    /** Python 非流式聊天响应。 */
    public record GraphChatResponse(String content,
                                    @JsonProperty("execution_id") String executionId) {
    }
}
