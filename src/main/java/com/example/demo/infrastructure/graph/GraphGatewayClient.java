package com.example.demo.infrastructure.graph;

import com.example.demo.email.domain.EmailMessage;
import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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
        this.webClient = webClientBuilder
                .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                .build();
    }

    /**
     * 将新邮件提交给 Python。Python 应负责 PostgreSQL 幂等入库并发布 RabbitMQ 事件。
     *
     * @param emailMessage Java 邮箱监听器解析后的邮件。
     * @param trigger 邮件触发来源。
     * @return Python 返回的邮件接收结果。
     */
    public Mono<EmailDispatchResponse> dispatchEmail(EmailMessage emailMessage, String trigger) {
        if (emailMessage.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("邮件缺少 userId，拒绝提交给 Graph"));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", emailMessage.getUserId());
        putNullable(payload, "email_config_id", emailMessage.getEmailConfigId());
        putNullable(payload, "provider", emailMessage.getProvider());
        putNullable(payload, "external_id", emailMessage.getExternalId());
        putNullable(payload, "message_id", emailMessage.getMessageId());
        putNullable(payload, "sender", emailMessage.getFrom());
        putNullable(payload, "sender_name", emailMessage.getFromName());
        payload.put("receiver", safeList(emailMessage.getTo()));
        payload.put("cc", safeList(emailMessage.getCc()));
        putNullable(payload, "subject", emailMessage.getSubject());
        putNullable(payload, "content", emailMessage.getTextContent());
        putNullable(payload, "html_content", emailMessage.getHtmlContent());
        putNullable(payload, "sent_at", emailMessage.getSentDate());
        putNullable(payload, "received_at", emailMessage.getReceivedDate());
        putNullable(payload, "account_email", emailMessage.getAccountEmail());
        putNullable(payload, "trigger", trigger);
        payload.put("attachment_count",
                emailMessage.getAttachments() == null ? 0 : emailMessage.getAttachments().size());
        payload.put("attachments",
                emailMessage.getAttachments() == null ? List.of() : emailMessage.getAttachments());

        return webClient.post()
                .uri("/internal/emails/dispatch")
                .headers(headers -> applyInternalHeaders(headers, emailMessage.getUserId()))
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
        if (properties.getInternalToken() != null && !properties.getInternalToken().isBlank()) {
            headers.set(INTERNAL_TOKEN_HEADER, properties.getInternalToken());
        }
    }

    private static void putNullable(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
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
