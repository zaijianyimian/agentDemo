package com.example.demo.Controller;


import com.example.demo.service.QwenChatService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class TestController {

    @Resource(name = "streamQwen")
    private StreamingChatModel streamingChatModel;

    @Resource
    private QwenChatService qwenChatService;

    @Resource
    private EmbeddingModel embeddingModel;

    /**
     * 普通聊天接口 - 返回完整响应
     */
    @GetMapping("/test")
    public String test(@RequestParam("prompt") String prompt) {
        return qwenChatService.complete(prompt);
    }

    /**
     * 流式聊天接口 - SSE 方式返回
     * 前端使用 EventSource 接收
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam("message") String message) {
        return qwenChatService.chat(message);
    }

    @GetMapping("/embedding")
    public float[] embedding(@RequestParam("text") String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content().vector();
    }

    @GetMapping("/embedding/full")
    public Response<Embedding> embeddingFull(@RequestParam("text") String text) {
        return embeddingModel.embed(text);
    }
}
