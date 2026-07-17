package com.example.demo.markdownskill.injector;

import com.example.demo.markdownskill.domain.MarkdownSkill;
import com.example.demo.markdownskill.registry.MarkdownSkillRegistry;
import com.example.demo.model.application.QwenChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 带 markdown skill 自动注入能力的聊天服务包装器。
 *
 * <p>该服务是 Plan A 的核心入口：</p>
 * <ul>
 *   <li>调用 {@link #chat(String)} / {@link #complete(String)} 时，先用 {@link MarkdownSkillRegistry}
 *       对用户消息做关键词匹配；</li>
 *   <li>命中阈值后，把命中 skill 的正文作为"前置上下文"拼到用户消息开头；</li>
 *   <li>再调用真正的 {@link QwenChatService} 走 LangChain4j 链路（包含 @Tool 调用）。</li>
 * </ul>
 *
 * <p>关闭方式：{@code app.markdown-skills.auto-inject=false} 时该 bean 仍装配但不做注入，
 * 行为退化为直接转发；可通过开关做 A/B 调试。</p>
 */
@Slf4j
@Component("markdownSkillChatService")
@ConditionalOnProperty(prefix = "app.markdown-skills", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MarkdownSkillChatService {

    /**
     * 真正的 LLM 服务，负责模型调用与 @Tool 分发。
     */
    private final QwenChatService qwenChatService;

    /**
     * skill 注册表，负责匹配。
     */
    private final MarkdownSkillRegistry registry;

    /**
     * 自动注入总开关，默认开启。
     */
    @Value("${app.markdown-skills.auto-inject:true}")
    private boolean autoInjectEnabled;

    /**
     * 注入上下文的最大字符数（防止 prompt 爆炸）。
     */
    @Value("${app.markdown-skills.inject-body-limit:4000}")
    private int injectBodyLimit;

    /**
     * 流式聊天入口：先做 skill 注入，再调 QwenChatService。
     *
     * @param userMessage 用户原始消息
     * @return 流式响应（Flux 不阻塞）
     */
    public Flux<String> chat(String userMessage) {
        String wrapped = wrapWithSkillContext(userMessage);
        return qwenChatService.chat(wrapped);
    }

    /**
     * 一次性聊天入口：先做 skill 注入，再调 QwenChatService。
     *
     * @param userMessage 用户原始消息
     * @return 完整响应
     */
    public String complete(String userMessage) {
        String wrapped = wrapWithSkillContext(userMessage);
        return qwenChatService.complete(wrapped);
    }

    /**
     * 把命中的 skill 正文作为前置上下文拼到用户消息前。
     * <p>无命中或自动注入关闭时，原样返回用户消息。</p>
     *
     * <p>拼接格式：</p>
     * <pre>{@code
     * <system-context>
     * [Skill: openspec-explore]
     * <skill body>
     * </system-context>
     *
     * <user-message>
     * <原始用户消息>
     * </user-message>
     * }</pre>
     */
    String wrapWithSkillContext(String userMessage) {
        if (userMessage == null) {
            return null;
        }
        if (!autoInjectEnabled) {
            return userMessage;
        }
        var match = registry.matchBest(userMessage);
        if (match.isEmpty()) {
            return userMessage;
        }
        MarkdownSkill skill = match.get().skill();
        String body = skill.getBody() == null ? "" : skill.getBody();
        if (body.length() > injectBodyLimit) {
            body = body.substring(0, injectBodyLimit) + "...";
        }
        log.info("自动注入 markdown skill: {} (得分={}, 命中长度={})",
                skill.getName(), match.get().score(), body.length());

        StringBuilder sb = new StringBuilder();
        sb.append("<system-context>\n");
        sb.append("下面是与当前用户问题最相关的 skill 指导（来自 ").append(skill.getName()).append("）：\n");
        sb.append("---\n");
        sb.append(body).append("\n");
        sb.append("---\n");
        sb.append("请遵循上述 skill 的指导来回答下面用户的问题。\n");
        sb.append("</system-context>\n\n");
        sb.append("<user-message>\n");
        sb.append(userMessage);
        sb.append("\n</user-message>");
        return sb.toString();
    }

    /**
     * 显式查询接口：返回当前消息匹配到的 skill（用于调试或前端展示）。
     */
    public java.util.Optional<MarkdownSkillRegistry.MatchResult> inspectMatch(String userMessage) {
        return registry.matchBest(userMessage);
    }
}