package com.example.demo.memory.web;

import com.example.demo.memory.domain.MemoryRecord;
import com.example.demo.memory.application.MemoryApplicationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记忆控制器
 * 提供长期记忆的提取存储与语义召回接口。
 */
@RestController
@RequestMapping("/api/memory")
@Validated
public class MemoryController {
    private final MemoryApplicationService memoryApplicationService;

    public MemoryController(MemoryApplicationService memoryApplicationService) {
        this.memoryApplicationService = memoryApplicationService;
    }

    /**
     * 从会话的最近消息中提取记忆并入库。
     */
    @PostMapping("/extract-store")
    public MemoryRecord extractStore(@RequestBody ExtractStoreRequest request) {
        return memoryApplicationService.extractAndStore(
                request.sessionId(),
                request.recentMessages()
        );
    }

    /**
     * 语义召回相关记忆，可按会话和分类过滤。
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam @NotBlank String query,
                                            @RequestParam(defaultValue = "5") int topK,
                                            @RequestParam(required = false) String sessionId,
                                            @RequestParam(required = false) String category) {
        return memoryApplicationService.recall(query, topK, sessionId, category);
    }

    public record ExtractStoreRequest(
            @NotBlank String sessionId,
            @NotEmpty List<String> recentMessages
    ) {
    }
}
