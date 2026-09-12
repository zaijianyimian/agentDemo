package com.example.demo.dispatch.application.decision;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.application.DispatchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Importance 分类。轻量规则实现，避免引入 LLM 调用。
 *
 * <p>规则（命中第一个就返回）：
 * <ol>
 *   <li>subject 含 {@code [urgent]} / {@code [高优]} / {@code 紧急} → high</li>
 *   <li>subject 含 {@code @agent} / {@code #todo} → medium</li>
 *   <li>默认 → low</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ImportanceClassifier {

    private final DispatchProperties properties;

    /**
     * 识别邮件主题的重要性等级（high / medium / low），按类注释规则匹配，命中即返回。
     */
    public String classify(String subject, String body) {
        if (subject == null) {
            return DispatchedTask.IMPORTANCE_LOW;
        }
        String s = subject.toLowerCase();
        if (s.contains("[urgent]") || s.contains("紧急") || s.contains("[高优]")) {
            return DispatchedTask.IMPORTANCE_HIGH;
        }
        if (s.contains("@agent") || s.contains("#todo") || s.contains("ai:")) {
            return DispatchedTask.IMPORTANCE_MEDIUM;
        }
        return DispatchedTask.IMPORTANCE_LOW;
    }
}
