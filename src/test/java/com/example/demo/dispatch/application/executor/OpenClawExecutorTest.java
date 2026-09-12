package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.application.ExecutorToggleService;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenClawExecutorTest {

    private HttpServer server;
    private ExecutorToggleService toggles;
    private DispatchProperties.OpenClaw properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        toggles = mock(ExecutorToggleService.class);
        when(toggles.isExecutorEnabled("openclaw")).thenReturn(true);
        properties = new DispatchProperties.OpenClaw();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("secret-token");
        properties.setModel("openclaw/test-agent");
        properties.setHealthTimeoutSeconds(1);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void reportsGatewayAvailabilityWithBearerToken() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"data\":[]}");
        });

        assertTrue(executor().isAvailable());
        assertEquals("Bearer secret-token", authorization.get());
    }

    @Test
    void reportsUnavailableWhenTokenIsMissing() {
        properties.setToken("");

        assertFalse(executor().isAvailable());
        assertThrows(Executor.ExecutorUnavailableException.class,
                () -> executor().execute(task(), "prompt", Path.of("."), 5));
    }

    @Test
    void executesChatCompletionWithConfiguredAgent() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"completed\"}}]}");
        });

        String result = executor().execute(task(), "do work", Path.of("/tmp/workspace"), 5);

        assertEquals("completed", result);
        assertEquals("Bearer secret-token", authorization.get());
        assertTrue(requestBody.get().contains("\"model\":\"openclaw/test-agent\""));
        assertTrue(requestBody.get().contains("\"content\":\"do work\""));
    }

    @Test
    void mapsAuthenticationAndGatewayFailures() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 401, "unauthorized"));
        assertThrows(Executor.ExecutorUnavailableException.class,
                () -> executor().execute(task(), "prompt", Path.of("."), 5));

        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 503, "upstream unavailable"));
        assertThrows(Executor.ExecutorFailedException.class,
                () -> executor().execute(task(), "prompt", Path.of("."), 5));
    }

    @Test
    void mapsRequestTimeout() {
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(1500);
                respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThrows(Executor.ExecutorTimeoutException.class,
                () -> executor().execute(task(), "prompt", Path.of("."), 1));
    }

    @Test
    void rejectsMalformedOrEmptyCompletion() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, "{\"choices\":[]}"));

        assertThrows(Executor.ExecutorFailedException.class,
                () -> executor().execute(task(), "prompt", Path.of("."), 5));
        assertEquals("", OpenClawExecutor.extractResult("not-json", objectMapper));
        assertEquals("", OpenClawExecutor.extractResult("", objectMapper));
    }

    private OpenClawExecutor executor() {
        return new OpenClawExecutor(toggles, properties, objectMapper, HttpClient.newHttpClient());
    }

    private DispatchedTask task() {
        return DispatchedTask.builder().sandboxLevel("workspace-write").build();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
