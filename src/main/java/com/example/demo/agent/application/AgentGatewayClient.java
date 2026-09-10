package com.example.demo.agent.application;

import com.example.demo.agent.dto.AgentTaskAcceptedResponse;
import com.example.demo.agent.dto.ChatRequest;
import com.example.demo.agent.dto.EmailTaskRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Java 业务层与远程 Agent Runtime 之间的稳定端口。 */
public interface AgentGatewayClient {

    /** 提交邮件任务。 */
    Mono<AgentTaskAcceptedResponse> submitEmail(EmailTaskRequest request);

    /** 执行非流式聊天。 */
    String chat(ChatRequest request);

    /** 执行流式聊天。 */
    Flux<String> streamChat(ChatRequest request);
}
