package com.example.demo.task.mcp;

import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.JobLog;
import com.example.demo.task.domain.ScheduledTask;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 暴露给 LLM（{@code QwenChatService}）的定时任务工具集。
 * <p>
 * 通过 LangChain4j 的 {@link Tool} 注解，让大模型在对话中直接增删改查用户的定时任务；
 * 所有参数统一用 {@code String}，便于 LLM 生成；时间字段用 ISO-8601 或 cron 字符串。
 *
 * <p>每个方法的语义约束：</p>
 * <ul>
 *   <li>修改/删除类操作需通过 {@code scheduleConfirmation=true} 确认；</li>
 *   <li>返回值是格式化文本，便于 LLM 拼接成自然语言回复；</li>
 *   <li>handler 名（{@code taskType}）限 {@code SKILL/CHAT/REMINDER}。</li>
 * </ul>
 */
@Slf4j
@Component("scheduleTaskTools")
@RequiredArgsConstructor
public class ScheduleTaskTools {

    private final ScheduledTaskService taskService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool("""
            创建一个新的定时任务。返回新建任务的 id 与下次执行时间。
            使用场景：用户说'明早8点提醒我提交周报'、'每小时检查一次邮箱'。
            必填参数：name(任务名)、taskType(SKILL|CHAT|REMINDER)、cronExpression(6字段cron表达式，例如 '0 0 8 * * ?' 代表每天8点)。
            可选参数：description(描述)、params(任务参数，CHAT类型时为对话内容，SKILL类型时为JSON字符串)、skillCode(SKILL类型时必填，对应技能代码)。
            """)
    public String create_scheduled_task(
            @P("任务名称，例如 '日报生成'、'早间提醒'") String name,
            @P("任务类型，可选值: SKILL(执行技能链) / CHAT(调LLM对话) / REMINDER(发邮件提醒)") String taskType,
            @P("6字段cron表达式，例如 '0 0 8 * * ?' 表示每天8点；'0 0/30 * * * ?' 表示每30分钟") String cronExpression,
            @P("任务描述，可为空") String description,
            @P("任务参数，CHAT类型时为提示词，SKILL类型时为JSON字符串如 '{\\\"key\\\":\\\"value\\\"}'，REMINDER类型可为空") String params,
            @P("SKILL类型时必填，对应技能代码；其它类型可为空") String skillCode) {

        ScheduledTask task = new ScheduledTask();
        task.setName(name);
        task.setTaskType(taskType);
        task.setCronExpression(cronExpression);
        task.setDescription(description);
        task.setParams(params);
        task.setSkillCode(skillCode);
        ScheduledTask created = taskService.createTask(task);
        return formatTask(created, "已创建定时任务");
    }

    @Tool("""
            列出所有定时任务，按更新时间倒序。
            返回简要信息：id、name、taskType、cronExpression、enabled、nextExecuteTime、executeCount。
            使用场景：用户问'我设了哪些定时任务'、'有哪些提醒任务'。
            """)
    public String list_scheduled_tasks() {
        List<ScheduledTask> tasks = taskService.listByUpdateTimeDesc();
        if (tasks.isEmpty()) {
            return "当前没有任何定时任务。";
        }
        StringBuilder sb = new StringBuilder("当前共有 ").append(tasks.size()).append(" 个定时任务：\n");
        for (ScheduledTask t : tasks) {
            sb.append("- [").append(t.getId()).append("] ")
                    .append(t.getName())
                    .append(" (").append(t.getTaskType()).append(")")
                    .append(" cron=").append(t.getCronExpression())
                    .append(" enabled=").append(Boolean.TRUE.equals(t.getEnabled()))
                    .append(" 下次=").append(formatTime(t.getNextExecuteTime()))
                    .append(" 执行次数=").append(t.getExecuteCount() == null ? 0 : t.getExecuteCount())
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool("""
            根据任务 id 获取定时任务的完整详情。
            返回字段：id、name、taskType、cronExpression、description、params、skillCode、
            enabled、lastExecuteTime、lastExecuteResult、nextExecuteTime、executeCount、successCount、failCount。
            使用场景：用户问'任务X是什么配置'、'任务X上次跑成功了没'。
            """)
    public String get_scheduled_task(@P("任务id") Long id) {
        ScheduledTask task = taskService.getTask(id);
        if (task == null) {
            return "任务不存在: id=" + id;
        }
        return formatTask(task, "任务详情");
    }

    @Tool("""
            更新已有定时任务的配置。只能修改提供的字段，未提供的字段保持原值。
            使用场景：用户说'把任务X改成每天9点'、'把任务Y的描述更新一下'。
            """)
    public String update_scheduled_task(
            @P("要更新的任务id") Long id,
            @P("新的任务名，传 null 表示不改") String name,
            @P("新的任务描述，传 null 表示不改") String description,
            @P("新的任务类型 SKILL/CHAT/REMINDER，传 null 表示不改") String taskType,
            @P("新的 cron 表达式，传 null 表示不改") String cronExpression,
            @P("新的参数，CHAT 类型时为提示词，传 null 表示不改") String params,
            @P("新的技能代码，传 null 表示不改") String skillCode) {

        ScheduledTask existing = taskService.getTask(id);
        if (existing == null) {
            return "任务不存在: id=" + id;
        }
        if (name != null) existing.setName(name);
        if (description != null) existing.setDescription(description);
        if (taskType != null) existing.setTaskType(taskType);
        if (cronExpression != null) existing.setCronExpression(cronExpression);
        if (params != null) existing.setParams(params);
        if (skillCode != null) existing.setSkillCode(skillCode);

        ScheduledTask updated = taskService.updateTask(existing);
        return formatTask(updated, "已更新定时任务");
    }

    @Tool("""
            启用或禁用定时任务。返回更新后的 enabled 状态。
            使用场景：用户说'暂停任务X'、'重新启用任务Y'。
            """)
    public String toggle_scheduled_task(@P("任务id") Long id) {
        ScheduledTask t = taskService.toggleTask(id);
        return "任务 [" + t.getId() + "] " + t.getName() + " 已"
                + (Boolean.TRUE.equals(t.getEnabled()) ? "启用" : "禁用")
                + "，下次执行时间: " + formatTime(t.getNextExecuteTime());
    }

    @Tool("""
            立即手动触发一次定时任务，不等待 cron 命中。
            使用场景：用户说'现在跑一下任务X'、'立刻执行早间提醒看看效果'。
            返回任务执行的简要结果。
            """)
    public String trigger_scheduled_task(@P("任务id") Long id) {
        try {
            String result = taskService.executeTask(id);
            return "任务 [" + id + "] 已触发，结果: " + truncate(result, 500);
        } catch (Exception e) {
            return "任务 [" + id + "] 触发失败: " + e.getMessage();
        }
    }

    @Tool("""
            删除一个定时任务。会同时清理该任务的执行日志。
            使用场景：用户说'删除任务X'、'我不再需要这个提醒了'。
            注意：删除不可恢复，请确认用户意图后再调用。
            """)
    public String delete_scheduled_task(@P("要删除的任务id") Long id) {
        ScheduledTask t = taskService.getTask(id);
        if (t == null) {
            return "任务不存在: id=" + id;
        }
        String name = t.getName();
        taskService.deleteTask(id);
        return "已删除定时任务 [" + id + "] " + name + "（含历史日志）";
    }

    @Tool("""
            查询某个任务的最近 N 条执行日志，用于排查失败原因。
            使用场景：用户问'任务X上次为什么失败'、'任务Y今天跑了几次'。
            返回每条日志的触发时间、状态、结果摘要、错误信息（如有）。
            """)
    public String list_execution_logs(@P("任务id") Long id, @P("返回条数，建议 5~20") int limit) {
        ScheduledTask t = taskService.getTask(id);
        if (t == null) {
            return "任务不存在: id=" + id;
        }
        List<JobLog> logs = taskService.recentLogs(id, limit <= 0 ? 10 : limit);
        if (logs.isEmpty()) {
            return "任务 [" + id + "] " + t.getName() + " 暂无执行日志。";
        }
        StringBuilder sb = new StringBuilder("任务 [").append(id).append("] ")
                .append(t.getName()).append(" 的最近 ").append(logs.size()).append(" 条日志：\n");
        for (JobLog log : logs) {
            sb.append("- ").append(formatTime(log.getTriggerTime()))
                    .append(" ").append(log.getStatus())
                    .append(" (").append(log.getDurationMs() == null ? 0 : log.getDurationMs()).append("ms")
                    .append(", ").append(log.getTriggerType()).append(")");
            if (log.getErrorMessage() != null && !log.getErrorMessage().isBlank()) {
                sb.append(" err=").append(truncate(log.getErrorMessage(), 120));
            } else if (log.getResult() != null && !log.getResult().isBlank()) {
                sb.append(" result=").append(truncate(log.getResult(), 120));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String formatTask(ScheduledTask t, String prefix) {
        return prefix + " [" + t.getId() + "] " + t.getName()
                + "\n  类型: " + t.getTaskType()
                + "\n  Cron: " + t.getCronExpression()
                + "\n  启用: " + Boolean.TRUE.equals(t.getEnabled())
                + "\n  下次执行: " + formatTime(t.getNextExecuteTime())
                + "\n  已执行: " + (t.getExecuteCount() == null ? 0 : t.getExecuteCount())
                + " 次 (成功 " + (t.getSuccessCount() == null ? 0 : t.getSuccessCount())
                + " / 失败 " + (t.getFailCount() == null ? 0 : t.getFailCount()) + ")"
                + (t.getDescription() != null ? "\n  描述: " + t.getDescription() : "")
                + (t.getSkillCode() != null ? "\n  技能代码: " + t.getSkillCode() : "")
                + (t.getParams() != null ? "\n  参数: " + t.getParams() : "");
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(FMT);
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }
}