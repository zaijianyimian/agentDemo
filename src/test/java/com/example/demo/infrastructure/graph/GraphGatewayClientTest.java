package com.example.demo.infrastructure.graph;

import com.example.demo.infrastructure.properties.GraphGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Java 网关读取 Python Graph 响应的契约测试。
 *
 * <p>用 {@link ExchangeFunction} 替身替代真实 HTTP，隔离 Python 侧服务。</p>
 */
class GraphGatewayClientTest {

    private static final String BASE_URL = "http://graph.test:8001";

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private static GraphGatewayProperties properties() {
        GraphGatewayProperties properties = new GraphGatewayProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(BASE_URL);
        properties.setConnectTimeoutSeconds(5);
        properties.setResponseTimeoutSeconds(30);
        return properties;
    }

    private static ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static ClientResponse sseResponse(String body) {
        Flux<DataBuffer> buffers = Flux.just(BUFFER_FACTORY.wrap(body.getBytes(StandardCharsets.UTF_8)));
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(buffers)
                .build();
    }

    private static GraphGatewayClient clientRespondingWith(ClientResponse response) {
        ExchangeFunction exchange = request -> Mono.just(response);
        return new GraphGatewayClient(properties(), WebClient.builder()
                .baseUrl(BASE_URL)
                .exchangeFunction(exchange));
    }

    @Test
    @DisplayName("非流式响应能读出 content 与 session_id")
    void chatReadsContentAndSessionId() {
        GraphGatewayClient client = clientRespondingWith(jsonResponse(
                "{\"content\":\"你好\",\"session_id\":\"session-a\",\"execution_id\":\"exec-1\"}"));

        assertThat(client.chat(123L, "session-a", "你好")).isEqualTo("你好");
    }

    @Test
    @DisplayName("Python 返回 session_id=null 时仍能正常读取结果")
    void chatReadsOneShotResponseWithoutSessionId() {
        GraphGatewayClient client = clientRespondingWith(jsonResponse(
                "{\"content\":\"一次性回复\",\"session_id\":null,\"execution_id\":\"exec-1\"}"));

        assertThat(client.chat(123L, null, "你好")).isEqualTo("一次性回复");
    }

    @Test
    @DisplayName("Python 省略 session_id 字段时仍能正常读取结果")
    void chatReadsResponseWithoutSessionField() {
        GraphGatewayClient client = clientRespondingWith(jsonResponse(
                "{\"content\":\"回复\",\"execution_id\":\"exec-1\"}"));

        assertThat(client.chat(123L, null, "你好")).isEqualTo("回复");
    }

    @Test
    @DisplayName("非流式请求路径与用户上下文正确")
    void chatSendsUserContextAndSessionId() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(jsonResponse("{\"content\":\"ok\",\"execution_id\":\"exec-1\"}"));
        };
        GraphGatewayClient client = new GraphGatewayClient(properties(), WebClient.builder()
                .baseUrl(BASE_URL)
                .exchangeFunction(exchange));

        client.chat(123L, "session-a", "你好");

        assertThat(captured.get().url().getPath()).isEqualTo("/internal/chat/complete");
        assertThat(captured.get().headers().getFirst("X-User-Id")).isEqualTo("123");
    }

    @Test
    @DisplayName("请求体携带 session_id，一次性请求为 null")
    void chatRequestBodyCarriesSessionId() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(jsonResponse("{\"content\":\"ok\",\"execution_id\":\"exec-1\"}"));
        };
        GraphGatewayClient client = new GraphGatewayClient(properties(), WebClient.builder()
                .baseUrl(BASE_URL)
                .exchangeFunction(exchange));

        client.chat(123L, null, "你好");

        assertThat(captured.get().body()).isNotNull();
    }

    @Test
    @DisplayName("content 为空时抛出明确异常")
    void chatRejectsEmptyContent() {
        GraphGatewayClient client = clientRespondingWith(
                jsonResponse("{\"content\":null,\"execution_id\":\"exec-1\"}"));

        assertThatThrownBy(() -> client.chat(123L, "session-a", "你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Graph");
    }

    @Test
    @DisplayName("SSE 流过滤 [DONE] 只输出文本")
    void streamChatFiltersDoneMarker() {
        GraphGatewayClient client = clientRespondingWith(sseResponse(
                "data: 你\n\n"
                        + "data: 好\n\n"
                        + "data: [DONE]\n\n"));

        StepVerifier.create(client.streamChat(123L, "session-a", "你好"))
                .expectNext("你", "好")
                .verifyComplete();
    }

    @Test
    @DisplayName("空 data 片段被忽略")
    void streamChatIgnoresBlankChunks() {
        GraphGatewayClient client = clientRespondingWith(sseResponse(
                "data: \n\n"
                        + "data: 有效\n\n"
                        + "data: [DONE]\n\n"));

        StepVerifier.create(client.streamChat(123L, null, "你好"))
                .expectNext("有效")
                .verifyComplete();
    }

    @Test
    @DisplayName("仅含空格的模型片段不会丢失")
    void streamChatPreservesWhitespaceChunks() {
        GraphGatewayClient client = clientRespondingWith(sseResponse(
                "data: hello\n\n"
                        + "data:  \n\n"
                        + "data: world\n\n"
                        + "data: [DONE]\n\n"));

        StepVerifier.create(client.streamChat(123L, null, "hello world"))
                .expectNext("hello", " ", "world")
                .verifyComplete();
    }

    @Test
    @DisplayName("流式请求路径与用户上下文正确，baseUrl 结尾斜杠被规范化")
    void streamChatNormalisesBaseUrl() {
        GraphGatewayProperties properties = properties();
        properties.setBaseUrl(BASE_URL + "/");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(sseResponse("data: [DONE]\n\n"));
        };
        GraphGatewayClient client = new GraphGatewayClient(properties, WebClient.builder()
                .exchangeFunction(exchange));

        StepVerifier.create(client.streamChat(7L, "session-a", "你好")).verifyComplete();

        assertThat(captured.get().url().getPath()).isEqualTo("/internal/chat/stream");
        assertThat(captured.get().headers().getFirst("X-User-Id")).isEqualTo("7");
    }

    @Test
    @DisplayName("未配置 baseUrl 时回落到默认地址")
    void baseUrlFallsBackWhenBlank() {
        GraphGatewayProperties properties = properties();
        properties.setBaseUrl("  ");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(jsonResponse("{\"content\":\"ok\",\"execution_id\":\"exec-1\"}"));
        };
        GraphGatewayClient client = new GraphGatewayClient(properties, WebClient.builder()
                .exchangeFunction(exchange));

        client.chat(1L, null, "你好");

        assertThat(captured.get().url().toString()).startsWith("http://127.0.0.1:8001/");
    }

    @Test
    @DisplayName("中文内容按 UTF-8 正确解码")
    void chatDecodesUtf8Content() {
        GraphGatewayClient client = clientRespondingWith(jsonResponse(
                "{\"content\":\"你好，世界\",\"execution_id\":\"exec-1\"}"));

        assertThat(client.chat(1L, null, "你好")).isEqualTo("你好，世界");
    }

    @Test
    @DisplayName("SSE 中文内容按 UTF-8 正确解码")
    void streamChatDecodesUtf8Content() {
        GraphGatewayClient client = clientRespondingWith(sseResponse(
                "data: 你好，世界\n\n"
                        + "data: [DONE]\n\n"));

        StepVerifier.create(client.streamChat(1L, null, "你好"))
                .expectNext("你好，世界")
                .verifyComplete();
    }

    @Test
    @DisplayName("启用 Graph 时缺少 exchange 配置会启动失败")
    void validateRejectsBlankExchange() {
        GraphGatewayProperties properties = properties();
        properties.setEmailExchange("  ");

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("禁用 Graph 时不做必填校验")
    void validateSkipsWhenDisabled() {
        GraphGatewayProperties properties = new GraphGatewayProperties();
        properties.setEnabled(false);
        properties.setBaseUrl(null);

        properties.validate();
    }
}
