package com.example.demo.system.events;

/**
 * 数据归档导入完成事件。
 * <p>
 * 由 {@link com.example.demo.system.application.DataArchiveService} 在数据归档成功导入后发布,
 * 供其他模块(如任务调度)监听并执行后续处理逻辑。
 */
public record DataArchiveImportedEvent() {
}
