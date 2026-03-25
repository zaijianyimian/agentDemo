package com.example.demo.controller;

import com.example.demo.memory.MemoryRecord;
import com.example.demo.service.MemoryApplicationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/memory")
@Validated
public class MemoryController {
    private final MemoryApplicationService memoryApplicationService;

    public MemoryController(MemoryApplicationService memoryApplicationService) {
        this.memoryApplicationService = memoryApplicationService;
    }

    @PostMapping("/extract-store")
    public MemoryRecord extractStore(@RequestBody ExtractStoreRequest request) {
        return memoryApplicationService.extractAndStore(
                request.sessionId(),
                request.recentMessages()
        );
    }

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
