package com.example.demo.dispatch.application.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateTest {

    private final PromptTemplate template = new PromptTemplate();

    @Test
    void renderHandlesEmptyRecall() {
        EmailMetadata email = new EmailMetadata("hello", "alice@example.com", "body text");
        String out = template.render(email, List.of(), "");
        assertTrue(out.contains("# 任务"));
        assertTrue(out.contains("# 邮件元数据"));
        assertTrue(out.contains("# 正文摘要"));
        assertTrue(out.contains("# 相关历史记忆"));
        assertTrue(out.contains("# 用户补充指令"));
        assertTrue(out.contains("（无相关历史记忆）"));
        assertTrue(out.contains("（无）"));
    }

    @Test
    void renderIncludesMemoriesAndHint() {
        EmailMetadata email = new EmailMetadata("s", "f", "b");
        List<Map<String, Object>> mems = List.of(
                Map.of("text", "first memory"),
                Map.of("text", "second memory"));
        String out = template.render(email, mems, "do this");
        assertTrue(out.contains("- first memory"));
        assertTrue(out.contains("- second memory"));
        assertTrue(out.contains("do this"));
    }

    @Test
    void hintMergerJoinsBothSources() {
        assertEquals("A\nB", HintMerger.merge("A", "B"));
        assertEquals("A", HintMerger.merge("A", null));
        assertEquals("B", HintMerger.merge("", "B"));
        assertEquals("", HintMerger.merge(null, null));
        assertEquals("", HintMerger.merge("  ", "\t"));
    }

    @Test
    void renderWithNullInputsDoesNotThrow() {
        EmailMetadata empty = EmailMetadata.empty();
        String out = template.render(empty, null, null);
        assertTrue(out.contains("# 任务"));
    }
}
