package com.example.demo.dispatch.application.prompt;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 纯模板拼接 prompt。不调用任何 LLM。
 *
 * <p>固定结构（顺序固定）：
 * <pre>
 *   # 任务
 *   # 邮件元数据 (主题 / 发件人)
 *   # 正文摘要
 *   # 相关历史记忆 (topK)
 *   # 用户补充指令
 * </pre>
 */
@Component
public class PromptTemplate {

    /**
     * 按类注释中的固定顺序把邮件元数据、记忆、用户指令拼成最终 prompt。
     */
    public String render(EmailMetadata email, List<Map<String, Object>> memories, String finalHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 任务\n\n");
        sb.append("请基于下面的邮件内容完成用户交给的任务。\n\n");

        sb.append("# 邮件元数据\n\n");
        sb.append("- 主题: ").append(nullSafe(email.subject())).append("\n");
        sb.append("- 发件人: ").append(nullSafe(email.from())).append("\n\n");

        sb.append("# 正文摘要\n\n");
        sb.append(nullSafe(email.bodyExcerpt())).append("\n\n");

        sb.append("# 相关历史记忆\n\n");
        if (memories == null || memories.isEmpty()) {
            sb.append("（无相关历史记忆）\n\n");
        } else {
            for (Map<String, Object> mem : memories) {
                String text = stringifyMemory(mem);
                if (text != null && !text.isBlank()) {
                    sb.append("- ").append(text).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("# 用户补充指令\n\n");
        if (finalHint == null || finalHint.isBlank()) {
            sb.append("（无）\n");
        } else {
            sb.append(finalHint).append("\n");
        }

        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String stringifyMemory(Map<String, Object> mem) {
        if (mem == null) {
            return null;
        }
        Object text = mem.get("text");
        if (text == null) {
            text = mem.get("content");
        }
        if (text == null) {
            text = mem.get("payload");
        }
        if (text != null) {
            return text.toString();
        }
        // fallback: dump the map
        return mem.toString();
    }
}
