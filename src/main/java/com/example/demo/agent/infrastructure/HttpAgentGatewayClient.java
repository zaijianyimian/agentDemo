package com.example.demo.agent.infrastructure;

import com.example.demo.agent.AgentProperties;
import com.example.demo.agent.application.AgentBadRequestException;
import com.example.demo.agent.application.AgentGatewayClient;
import com.example.demo.agent.application.AgentGatewayException;
import com.example.demo.agent.application.AgentRateLimitException;
import com.example.demo.agent.application.AgentTimeoutException;
import com.example.demo.agent.application.AgentUnauthorizedException;
import com.example.demo.agent.application.AgentUnavailableException;
import com.example.demo.agent.dto.AgentTaskAcceptedResponse;
import com.example.demo.agent.dto.ChatRequest;
import com.example.demo.agent.dto.ChatResponse;
import com.example.demo.agent.dto.EmailTaskRequest;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** 基于 HTTP/SSE 的远程 Agent Gateway 实现。 */
@Component
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "remote")
public class HttpAgentGatewayClient implements AgentGatewayClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Agent-Internal-Token";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final AgentProperties properties;
    private final WebClient webClient;

    public HttpAgentGatewayClient(AgentProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        AgentProperties.Remote remote = properties.getRemote();
        int connectTimeoutMillis = Math.toIntExact(
                Duration.ofSeconds(remote.getConnectTimeoutSeconds()).toMillis());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofSeconds(remote.getResponseTimeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(stripTrailingSlash(remote.getBaseUrl()))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<AgentTaskAcceptedResponse> submitEmail(EmailTaskRequest request) {
        if (request.userId() == null) {
            return Mono.error(new AgentBadRequestException("邮件任务缺少 userId"));
        }
        Mono<AgentTaskAcceptedResponse> invocation = webClient.post()
                .uri("/internal/emails/dispatch")
                .headers(headers -> applyInternalHeaders(headers, request.userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toGatewayError)
                .bodyToMono(AgentTaskAcceptedResponse.class)
                .timeout(responseTimeout());
        return withRetry(invocation)
                .map(response -> response.withFallbackTaskId(request.taskId()));
    }

    @Override
    public String chat(ChatRequest request) {
        Mono<ChatResponse> invocation = webClient.post()
                .uri("/internal/chat/complete")
                .headers(headers -> applyInternalHeaders(headers, request.userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toGatewayError)
                .bodyToMono(ChatResponse.class)
                .timeout(responseTimeout());
        ChatResponse response = withRetry(invocation).block(responseTimeout().plusSeconds(1));
        if (response == null || response.content() == null) {
            throw new AgentUnavailableException("远程 Agent 未返回有效聊天内容");
        }
        return response.content();
    }

    @Override
    public Flux<String> streamChat(ChatRequest request) {
        return webClient.post()
                .uri("/internal/chat/stream")
                .headers(headers -> applyInternalHeaders(headers, request.userId()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toGatewayError)
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .handle((event, sink) -> {
                    String data = event.data();
                    if (data != null && !data.isBlank() && !"[DONE]".equals(data)) {
                        sink.next(data);
                    }
                })
                .onErrorMap(this::mapTransportError);
    }

    private <T> Mono<T> withRetry(Mono<T> invocation) {
        Mono<T> classified = invocation.onErrorMap(this::mapTransportError);
        int maxRetries = properties.getRemote().getMaxRetries();
        if (maxRetries == 0) {
            return classified;
        }
        return classified.retryWhen(Retry.backoff(
                        maxRetries,
                        Duration.ofMillis(Math.max(1L, properties.getRemote().getRetryBackoffMillis())))
                .filter(this::isRetryable)
                .jitter(0.2));
    }

    private boolean isRetryable(Throwable error) {
        return error instanceof AgentUnavailableException
                || error instanceof AgentTimeoutException
                || error instanceof AgentRateLimitException;
    }

    private Mono<? extends Throwable> toGatewayError(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> classifyStatus(status, body));
    }

    private AgentGatewayException classifyStatus(int status, String body) {
        String detail = summarize(body);
        if (status == 401 || status == 403) {
            return new AgentUnauthorizedException("Agent Gateway 鉴权失败，HTTP " + status + detail);
        }
        if (status == 408 || status == 504) {
            return new AgentTimeoutException("Agent Gateway 超时，HTTP " + status + detail);
        }
        if (status == 429) {
            return new AgentRateLimitException("Agent Gateway 限流，HTTP 429" + detail);
        }
        if (status >= 500) {
            return new AgentUnavailableException("Agent Gateway 暂不可用，HTTP " + status + detail);
        }
        if (status >= 400) {
            return new AgentBadRequestException("Agent Gateway 拒绝请求，HTTP " + status + detail);
        }
        return new AgentGatewayException("Agent Gateway 异常，HTTP " + status + detail);
    }

    private Throwable mapTransportError(Throwable error) {
        if (error instanceof AgentGatewayException) {
            return error;
        }
        if (error instanceof TimeoutException) {
            return new AgentTimeoutException("Agent Gateway 请求超时", error);
        }
        if (error instanceof WebClientRequestException) {
            return new AgentUnavailableException("无法连接 Agent Gateway", error);
        }
        return new AgentGatewayException("Agent Gateway 调用失败", error);
    }

    private void applyInternalHeaders(org.springframework.http.HttpHeaders headers, long userId) {
        headers.set(USER_ID_HEADER, String.valueOf(userId));
        headers.set(INTERNAL_TOKEN_HEADER, properties.getRemote().getInternalToken());
    }

    private Duration responseTimeout() {
        return Duration.ofSeconds(properties.getRemote().getResponseTimeoutSeconds());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 300) {
            normalized = normalized.substring(0, 300) + "...";
        }
        return ": " + normalized;
    }
}
