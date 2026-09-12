package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.domain.DispatchedTask;

import java.util.List;
import java.util.Set;

/**
 * 把 {@link DispatchedTask#sandboxLevel} 翻译成各执行器原生的 flag 集合。
 *
 * <p>三级 sandbox:
 * <ul>
 *   <li>{@code read-only} —— 只读，禁写</li>
 *   <li>{@code workspace-write} —— 工作区内可写</li>
 *   <li>{@code danger-full-access} —— 完全访问（仍受 worktree 边界保护）</li>
 * </ul>
 */
public final class SandboxTranslator {

    public static final String READ_ONLY = "read-only";
    public static final String WORKSPACE_WRITE = "workspace-write";
    public static final String DANGER_FULL = "danger-full-access";

    private static final Set<String> VALID = Set.of(READ_ONLY, WORKSPACE_WRITE, DANGER_FULL);

    private SandboxTranslator() {}

    /**
     * 校验 sandbox 等级是否是三种合法值之一。
     */
    public static boolean isValid(String sandboxLevel) {
        return sandboxLevel != null && VALID.contains(sandboxLevel);
    }

    /**
     * Codex 的 {@code --sandbox} flag 值。
     */
    public static String codexFlag(String sandboxLevel) {
        if (!isValid(sandboxLevel)) {
            throw new IllegalArgumentException("invalid sandbox level: " + sandboxLevel);
        }
        return switch (sandboxLevel) {
            case READ_ONLY -> "read-only";
            case WORKSPACE_WRITE -> "workspace-write";
            case DANGER_FULL -> "danger-full-access";
            default -> throw new IllegalStateException("unreachable");
        };
    }

    /**
     * Claude Code 的 {@code --allowedTools} 列表。
     *
     * <p>三级映射：
     * <ul>
     *   <li>read-only → 只读工具集（Read / Glob / Grep）</li>
     *   <li>workspace-write → 读写工具集（不含 Bash）</li>
     *   <li>danger-full-access → 全工具（含 Bash）</li>
     * </ul>
     */
    public static List<String> claudeTools(String sandboxLevel) {
        if (!isValid(sandboxLevel)) {
            throw new IllegalArgumentException("invalid sandbox level: " + sandboxLevel);
        }
        return switch (sandboxLevel) {
            case READ_ONLY -> List.of("Read", "Glob", "Grep");
            case WORKSPACE_WRITE -> List.of("Read", "Edit", "Write", "Glob", "Grep", "WebFetch", "WebSearch");
            case DANGER_FULL -> List.of("Read", "Edit", "Write", "Glob", "Grep", "Bash", "WebFetch", "WebSearch");
            default -> throw new IllegalStateException("unreachable");
        };
    }
}
