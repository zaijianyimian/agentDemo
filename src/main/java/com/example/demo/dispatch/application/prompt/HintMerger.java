package com.example.demo.dispatch.application.prompt;

/**
 * 合并 per-email 默认 hint 与邮件内识别的 user_hint。
 *
 * <p>规则：双源以换行分隔，任一边为空则返回另一边。</p>
 */
public final class HintMerger {

    private HintMerger() {}

    /**
     * 合并 per-email 默认 hint 与本次邮件内的 user_hint。双源以换行分隔，任一为空返回另一源。
     */
    public static String merge(String agentDefaultHint, String userHint) {
        boolean a = agentDefaultHint != null && !agentDefaultHint.isBlank();
        boolean b = userHint != null && !userHint.isBlank();
        if (a && b) {
            return agentDefaultHint + "\n" + userHint;
        }
        if (a) {
            return agentDefaultHint;
        }
        if (b) {
            return userHint;
        }
        return "";
    }
}
