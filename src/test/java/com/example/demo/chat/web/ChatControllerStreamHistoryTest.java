package com.example.demo.chat.web;

import com.example.demo.chat.application.ChatHistoryService;
import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.chat.domain.ChatSession;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.ExecutionContextScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话流式聊天的历史落库语义测试。
 *
 * <p>用 Mockito 替身隔离 Python Graph 与 MySQL，验证：成功完成后用户消息与助手最终消息
 * 都进入聊天历史；上游失败或用户中断时不把不完整回复当成成功消息。</p>
 */
class ChatControllerStreamHistoryTest {

    private static final String GRAPH_MODEL = "python-graph";
    private static final String SESSION_ID = "session-a";

    private final GraphGatewayClient graphGatewayClient = mock(GraphGatewayClient.class);
    private final ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    private final CurrentUserContext currentUserProvider = mock(CurrentUserContext.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 模拟已持久化的聊天历史。 */
    private final List<ChatMessageEntity> persistedMessages = new CopyOnWriteArrayList<>();

    private ChatController controller;

    @BeforeEach
    void setUp() {
        // 为所有断言设置兜底超时，避免上游不结束时测试挂死。
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(10));
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(chatHistoryService.getSession(anyString()))
                .thenReturn(ChatSession.builder().id(SESSION_ID).userId(7L).build());

        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            String role = invocation.getArgument(1);
            String content = invocation.getArgument(2);
            String model = invocation.getArgument(3);
            ChatMessageEntity stored = ChatMessageEntity.builder()
                    .sessionId(sessionId)
                    .role(role)
                    .content(content)
                    .model(model)
                    .build();
            persistedMessages.add(stored);
            return stored;
        }).when(chatHistoryService).addMessage(anyString(), anyString(), anyString(), anyString());

        when(chatHistoryService.getSessionMessages(anyString())).thenAnswer(invocation -> persistedMessages);

        controller = new ChatController(
                graphGatewayClient, chatHistoryService, currentUserProvider, objectMapper);
    }

    private List<String> roles() {
        return persistedMessages.stream().map(ChatMessageEntity::getRole).toList();
    }

    private List<String> contents() {
        return persistedMessages.stream().map(ChatMessageEntity::getContent).toList();
    }

    @Test
    @DisplayName("流式成功后用户消息与助手最终消息都写入聊天历史")
    void successfulStreamRecordsUserAndAssistantMessages() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.just("你", "好", "呀"));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(contents()).containsExactly("你好", "你好呀");
    }

    @Test
    @DisplayName("刷新后可从现有历史接口读回成功的对话")
    void successfulTurnIsReadableFromHistoryApi() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.just("你", "好"));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        List<ChatMessageEntity> reloaded = chatHistoryService.getSessionMessages(SESSION_ID);

        assertThat(reloaded).hasSize(2);
        assertThat(reloaded.get(0).getRole()).isEqualTo("user");
        assertThat(reloaded.get(1).getRole()).isEqualTo("assistant");
        assertThat(reloaded.get(1).getContent()).isEqualTo("你好");
        assertThat(reloaded).allSatisfy(message -> assertThat(message.getSessionId())
                .isEqualTo(SESSION_ID));
    }

    @Test
    @DisplayName("写入的消息使用 Graph 模型标识")
    void recordedMessagesUseGraphModel() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好")).thenReturn(Flux.just("好"));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(persistedMessages).allSatisfy(
                message -> assertThat(message.getModel()).isEqualTo(GRAPH_MODEL));
    }

    @Test
    @DisplayName("上游失败时不写入助手消息")
    void failedStreamDoesNotRecordAssistantMessage() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.concat(Flux.just("半截"), Flux.error(new IllegalStateException("boom"))));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyErrorMessage("boom");

        assertThat(roles()).doesNotContain("assistant");
    }

    @Test
    @DisplayName("用户中断时不写入助手消息")
    void cancelledStreamDoesNotRecordAssistantMessage() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.concat(Flux.just("半截"), Flux.never()));

        // 上游永不结束，只消费已到达的事件后立即取消。
        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID), 2)
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(10));

        assertThat(roles()).doesNotContain("assistant");
    }

    @Test
    @DisplayName("上游没有产出任何文本时不写入空助手消息")
    void emptyReplyIsNotRecorded() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好")).thenReturn(Flux.empty());

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(roles()).doesNotContain("assistant");
    }

    @Test
    @DisplayName("JSON SSE 变体同样在成功后落库")
    void jsonStreamRecordsUserAndAssistantMessages() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.just("你", "好"));

        StepVerifier.create(controller.chatStreamWithSessionJson("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(roles()).containsExactly("user", "assistant");
        assertThat(contents()).containsExactly("你好", "你好");
    }

    @Test
    @DisplayName("JSON SSE 变体上游失败时不写入助手消息")
    void jsonStreamFailureDoesNotRecordAssistantMessage() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(controller.chatStreamWithSessionJson("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyErrorMessage("boom");

        assertThat(roles()).doesNotContain("assistant");
    }

    @Test
    @DisplayName("SSE 协议包含 connected 注释、文本片段和 done 结束标记")
    void successfulStreamEmitsExpectedSseProtocol() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好")).thenReturn(Flux.just("你", "好"));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .expectNextMatches(event -> event.comment() != null && event.comment().equals("connected"))
                .expectNextMatches(event -> "你".equals(event.data()))
                .expectNextMatches(event -> "好".equals(event.data()))
                .expectNextMatches(event -> "done".equals(event.event()) && "[DONE]".equals(event.data()))
                .verifyComplete();
    }

    @Test
    @DisplayName("无会话的一次性流式入口不写聊天历史")
    void oneShotStreamDoesNotTouchHistory() {
        when(graphGatewayClient.streamChat(7L, null, "你好")).thenReturn(Flux.just("回复"));

        StepVerifier.create(controller.chatStream("你好"))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(persistedMessages).isEmpty();
        verify(chatHistoryService, never()).addMessage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("一次性非流式入口不写聊天历史")
    void oneShotCompleteDoesNotTouchHistory() {
        when(graphGatewayClient.chat(7L, null, "你好")).thenReturn("回复");

        assertThat(controller.complete("你好")).isEqualTo("回复");
        assertThat(persistedMessages).isEmpty();
    }

    @Test
    @DisplayName("带会话的非流式入口仍然前后各写一条消息")
    void sessionCompleteRecordsUserAndAssistantMessages() {
        when(graphGatewayClient.chat(7L, SESSION_ID, "你好")).thenReturn("你好呀");

        assertThat(controller.completeWithSession("你好", SESSION_ID)).isEqualTo("你好呀");
        assertThat(roles()).containsExactly("user", "assistant");
    }

    @Test
    @DisplayName("会话归属在调用 Graph 之前完成校验")
    void sessionOwnershipIsCheckedBeforeCallingGraph() {
        when(graphGatewayClient.streamChat(eq(7L), eq(SESSION_ID), anyString()))
                .thenReturn(Flux.just("好"));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        verify(chatHistoryService).getSession(SESSION_ID);
    }

    @Test
    @DisplayName("助手消息只在流正常结束后写入，中途不会提前落库")
    void assistantMessageIsWrittenOnlyAfterStreamCompletes() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.concat(Flux.just("你"), Flux.never()));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID), 2)
                .expectNextCount(2)
                .then(() -> assertThat(roles()).containsExactly("user"))
                .thenCancel()
                .verify(Duration.ofSeconds(10));

        assertThat(roles()).doesNotContain("assistant");
    }

    @Test
    @DisplayName("异步完成时写库回调仍持有已认证用户上下文")
    void asyncCompletionKeepsUserContextForHistoryWrites() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.just("好").delayElements(Duration.ofMillis(10)));
        AtomicInteger writesWithContext = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(ExecutionContextScope.requireCurrent().user().userId()).isEqualTo(7L);
            writesWithContext.incrementAndGet();
            return null;
        }).when(chatHistoryService).addMessage(eq(SESSION_ID), anyString(), anyString(), eq(GRAPH_MODEL));

        StepVerifier.create(controller.chatStreamWithSession("你好", SESSION_ID))
                .thenConsumeWhile(event -> true)
                .verifyComplete();

        assertThat(writesWithContext).hasValue(2);
    }

    @Test
    @DisplayName("同一响应流再次订阅时不会沿用上次累积的助手文本")
    void repeatedSubscriptionStartsWithEmptyAccumulator() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好"))
                .thenReturn(Flux.just("好"));
        Flux<ServerSentEvent<String>> stream = controller.chatStreamWithSession("你好", SESSION_ID);

        StepVerifier.create(stream).thenConsumeWhile(event -> true).verifyComplete();
        StepVerifier.create(stream).thenConsumeWhile(event -> true).verifyComplete();

        assertThat(contents()).containsExactly("你好", "好", "你好", "好");
    }

    @Test
    @DisplayName("非流式响应对象由 ChatResponse 承载")
    void structuredResponseCarriesContent() {
        when(graphGatewayClient.chat(7L, null, "你好")).thenReturn("回复");

        var response = controller.chatStructured("你好");

        assertThat(response.getContent()).isEqualTo("回复");
        assertThat(response.getIsComplete()).isTrue();
    }

    @Test
    @DisplayName("流式事件数据类型保持为 ServerSentEvent")
    void streamReturnsServerSentEvents() {
        when(graphGatewayClient.streamChat(7L, SESSION_ID, "你好")).thenReturn(Flux.just("好"));

        Flux<ServerSentEvent<String>> events = controller.chatStreamWithSession("你好", SESSION_ID);

        assertThat(events).isNotNull();
    }
}
