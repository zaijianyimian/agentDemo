package com.example.demo.dispatch.application.decision;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从邮件主题或正文中识别 hint 标记，写入 {@code user_hint}。
 *
 * <p>支持的标记（按优先级匹配，找到第一个就停）：
 * <ol>
 *   <li>{@code @agent <指令>} — 主题或正文首行</li>
 *   <li>{@code AI: <指令>} — 主题或正文首行</li>
 *   <li>{@code #todo <指令>} — 主题或正文首行</li>
 * </ol>
 */
public final class HintDetector {

    private static final Pattern[] PATTERNS = new Pattern[]{
            Pattern.compile("(?m)^@agent\\s+(.+)$"),
            Pattern.compile("(?m)^AI:\\s*(.+)$"),
            Pattern.compile("(?m)^#todo\\s+(.+)$")
    };

    private HintDetector() {}

    /**
     * 提取首个匹配的 hint 指令文本。优先看主题，其次看正文首段。命中即返回。
     */
    public static String extract(String subject, String body) {
        if (subject != null) {
            for (Pattern p : PATTERNS) {
                Matcher m = p.matcher(subject);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        }
        if (body != null) {
            // 仅在正文首段（第一个换行前）查找
            int nl = body.indexOf('\n');
            String head = nl >= 0 ? body.substring(0, nl) : body;
            for (Pattern p : PATTERNS) {
                Matcher m = p.matcher(head);
                if (m.find()) {
                    return m.group(1).trim();
                }
            }
        }
        return null;
    }
}
