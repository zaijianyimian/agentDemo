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
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python Graph 内部 API 客户端。
 *
 * <p>本客户端是 Java 与 Python Agent 服务之间唯一的 HTTP/SSE 边界。Java 不连接 Python 使用的
 * PostgreSQL，也不感知其表结构；所有 AI 数据写入与查询均由 Python API 完成。</p>
 */
@Component
public class GraphGatewayClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
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
     * 将新邮件提交给 Python。Python 应负责 PostgreSQL 幂等入库并发布 RabbitMQ 事件。
     *
     * @param emailMessage Java 邮箱监听器解析后的邮件。
     * @param trigger 邮件触发来源。
     * @return Python 返回的邮件接收结果。
     */
    public Mono<EmailDispatchResponse> dispatchEmail(long userId, Map<String, Object> payload) {
        if (userId <= 0) {
            return Mono.error(new IllegalArgumentException("邮件缺少有效 userId，拒绝提交给 Graph"));
        }
        return webClient.post()
                .uri("/internal/emails/dispatch")
                .headers(headers -> applyInternalHeaders(headers, userId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(EmailDispatchResponse.class)
                .timeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()));
    }

    /**
     * 调用 Python Graph 完成一次非流式 Agent 对话。
     *
     * @param userId 当前用户 ID。
     * @param sessionId Java 会话 ID；无会话时可为空。
     * @param message 用户消息。
     * @return Agent 最终文本。
     */
    public String chat(long userId, String sessionId, String message) {
        GraphChatResponse response = webClient.post()
                .uri("/internal/chat/complete")
                .headers(headers -> applyInternalHeaders(headers, userId))
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
     * @param sessionId Java 会话 ID；无会话时可为空。
     * @param message 用户消息。
     * @return Agent 文本数据流，不包含 Python 的结束标记。
     */
    public Flux<String> streamChat(long userId, String sessionId, String message) {
        return webClient.post()
                .uri("/internal/chat/stream")
                .headers(headers -> applyInternalHeaders(headers, userId))
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

    private void applyInternalHeaders(org.springframework.http.HttpHeaders headers, long userId) {
        headers.set(USER_ID_HEADER, String.valueOf(userId));
        headers.set(INTERNAL_TOKEN_HEADER, properties.getInternalToken());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** Python 邮件接收接口响应。 */
    public record EmailDispatchResponse(
            @JsonProperty("email_id") Long emailId,
            String status,
            boolean duplicate) {
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
