package com.example.demo.system.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务端数据备份配置。
 *
 * <p>启用后会把全量数据 ZIP 快照落地到 {@code backup.directory}，
 * 由 {@code BackupService} 负责创建、列出、下载、删除与清理。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    /**
     * 备份文件存储目录（相对项目根或绝对路径）。
     */
    private String directory = "data/backups";

    /**
     * 单次清理时按 mtime 倒序保留的最大份数。
     */
    private int keepMostRecent = 10;

    /**
     * 备份保留天数，超过则会被清理。
     */
    private int retentionDays = 30;

    /**
     * 备份目录的总大小上限（MB），0 表示不限制。
     * 超出后按 mtime 从最旧的开始删除，直到目录总大小不超阈值。
     */
    private long maxTotalSizeMb = 0L;

    /**
     * 文件名前缀。
     */
    private String fileNamePrefix = "agent-data-backup";
}