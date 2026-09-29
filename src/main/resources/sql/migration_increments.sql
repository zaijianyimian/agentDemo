-- ============================================
-- AI Agent 数据库非破坏性增量迁移脚本
-- 创建时间: 2026-04-06
-- 版本: 4.0
-- 说明:
--   1) 不执行 DROP TABLE / DROP COLUMN
--   2) 保留历史数据
--   3) 使用动态SQL检测并添加缺失的列/表
-- 适用于: 已有数据库的版本升级
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;
SET @db_name = DATABASE();

-- ============================================
-- 0. 邮件监听配置增量迁移
-- ============================================

SET @has_email_listen_start_time = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'listen_start_time'
);

SET @sql_email_listen_start_time = IF(
    @has_email_listen_start_time = 0,
    'ALTER TABLE `email_config` ADD COLUMN `listen_start_time` TIME DEFAULT NULL COMMENT ''监听开始时间，为空表示全天监听'' AFTER `poll_interval`',
    'SELECT ''email_config.listen_start_time exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_listen_start_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_email_listen_end_time = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'listen_end_time'
);

SET @sql_email_listen_end_time = IF(
    @has_email_listen_end_time = 0,
    'ALTER TABLE `email_config` ADD COLUMN `listen_end_time` TIME DEFAULT NULL COMMENT ''监听结束时间，为空表示全天监听'' AFTER `listen_start_time`',
    'SELECT ''email_config.listen_end_time exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_listen_end_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_email_provider = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'provider'
);

SET @sql_email_provider = IF(
    @has_email_provider = 0,
    'ALTER TABLE `email_config` ADD COLUMN `provider` VARCHAR(40) DEFAULT NULL COMMENT ''邮箱提供商: GENERIC_IMAP/GENERIC_POP3/GMAIL_API/MICROSOFT_GRAPH'' AFTER `protocol`',
    'SELECT ''email_config.provider exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_provider;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_email_listen_mode = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'listen_mode'
);

SET @sql_email_listen_mode = IF(
    @has_email_listen_mode = 0,
    'ALTER TABLE `email_config` ADD COLUMN `listen_mode` VARCHAR(40) DEFAULT NULL COMMENT ''监听模式: POLLING/IMAP_IDLE/WEBHOOK/DELTA_SYNC'' AFTER `provider`',
    'SELECT ''email_config.listen_mode exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_listen_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_email_fallback_listen_mode = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'fallback_listen_mode'
);

SET @sql_email_fallback_listen_mode = IF(
    @has_email_fallback_listen_mode = 0,
    'ALTER TABLE `email_config` ADD COLUMN `fallback_listen_mode` VARCHAR(40) DEFAULT NULL COMMENT ''监听失败后的降级监听模式'' AFTER `listen_mode`',
    'SELECT ''email_config.fallback_listen_mode exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_fallback_listen_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_email_provider_settings = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND column_name = 'provider_settings'
);

SET @sql_email_provider_settings = IF(
    @has_email_provider_settings = 0,
    'ALTER TABLE `email_config` ADD COLUMN `provider_settings` TEXT DEFAULT NULL COMMENT ''提供商非敏感扩展配置JSON'' AFTER `fallback_listen_mode`',
    'SELECT ''email_config.provider_settings exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_provider_settings;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `email_config`
SET `provider` = CASE
    WHEN LOWER(COALESCE(`protocol`, 'imap')) = 'pop3' THEN 'GENERIC_POP3'
    ELSE 'GENERIC_IMAP'
END
WHERE `provider` IS NULL OR `provider` = '';

UPDATE `email_config`
SET `listen_mode` = 'POLLING'
WHERE `listen_mode` IS NULL OR `listen_mode` = '';

SET @has_email_config_provider_mode_idx = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'email_config'
      AND index_name = 'idx_email_config_provider_mode'
);

SET @sql_email_config_provider_mode_idx = IF(
    @has_email_config_provider_mode_idx = 0,
    'ALTER TABLE `email_config` ADD INDEX `idx_email_config_provider_mode` (`provider`, `listen_mode`)',
    'SELECT ''email_config.idx_email_config_provider_mode exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_config_provider_mode_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `email_listener_state` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_id` BIGINT NOT NULL COMMENT '邮箱配置ID',
    `provider` VARCHAR(40) NOT NULL COMMENT '邮箱提供商',
    `listen_mode` VARCHAR(40) NOT NULL COMMENT '监听模式',
    `fallback_listen_mode` VARCHAR(40) DEFAULT NULL COMMENT '降级监听模式',
    `cursor_type` VARCHAR(40) DEFAULT NULL COMMENT '游标类型',
    `cursor_value` LONGTEXT DEFAULT NULL COMMENT '游标值',
    `subscription_id` VARCHAR(255) DEFAULT NULL COMMENT 'Webhook/订阅ID',
    `subscription_expire_time` DATETIME DEFAULT NULL COMMENT '订阅过期时间',
    `webhook_resource` VARCHAR(500) DEFAULT NULL COMMENT 'Webhook资源或scope',
    `recent_message_keys` LONGTEXT DEFAULT NULL COMMENT '近期已处理消息key JSON',
    `status` VARCHAR(40) DEFAULT 'STOPPED' COMMENT '监听状态',
    `last_success_time` DATETIME DEFAULT NULL COMMENT '最后成功时间',
    `last_error_time` DATETIME DEFAULT NULL COMMENT '最后错误时间',
    `last_error` TEXT DEFAULT NULL COMMENT '最后错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_listener_state_config` (`config_id`),
    INDEX `idx_email_listener_state_provider_mode` (`provider`, `listen_mode`),
    INDEX `idx_email_listener_state_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱监听状态表';

SET @sql_email_password_nullable = 'ALTER TABLE `email_config` MODIFY COLUMN `password` VARCHAR(255) DEFAULT NULL COMMENT ''邮箱授权码/密码(加密存储)''';
PREPARE stmt FROM @sql_email_password_nullable;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 1. user_account 表增量迁移
-- ============================================

-- 添加 token_version 列（若不存在）
SET @has_token_version = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'user_account'
      AND column_name = 'token_version'
);

SET @sql_token_version = IF(
    @has_token_version = 0,
    'ALTER TABLE `user_account` ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''JWT版本号，用于令牌失效'' AFTER `email_verified`',
    'SELECT ''user_account.token_version exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_token_version;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `user_account` SET `token_version` = 0 WHERE `token_version` IS NULL;

-- 添加 face_auth_enabled 列（若不存在）
SET @has_face_auth_enabled = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'user_account'
      AND column_name = 'face_auth_enabled'
);

SET @sql_face_auth = IF(
    @has_face_auth_enabled = 0,
    'ALTER TABLE `user_account` ADD COLUMN `face_auth_enabled` TINYINT(1) DEFAULT 0 COMMENT ''是否要求登录时进行人脸二次验证'' AFTER `email_verified`',
    'SELECT ''user_account.face_auth_enabled exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_face_auth;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `user_account` SET `face_auth_enabled` = 0 WHERE `face_auth_enabled` IS NULL;

-- ============================================
-- 2. 创建缺失的认证相关表
-- ============================================

CREATE TABLE IF NOT EXISTS `user_face_profile` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `embedding` LONGTEXT NOT NULL COMMENT '人脸向量(JSON数组)',
    `vector_dimension` INT NOT NULL COMMENT '向量维度',
    `quality_score` DOUBLE DEFAULT NULL COMMENT '图像质量分(0-1)',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_face_profile_user_id` (`user_id`),
    INDEX `idx_user_face_profile_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户人脸认证向量表';

CREATE TABLE IF NOT EXISTS `oauth_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
    `provider` VARCHAR(30) NOT NULL COMMENT '提供商: github',
    `provider_user_id` VARCHAR(100) NOT NULL COMMENT '第三方用户ID',
    `login` VARCHAR(100) DEFAULT NULL COMMENT '第三方登录名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_oauth_provider_user` (`provider`, `provider_user_id`),
    INDEX `idx_oauth_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方账号绑定表';

CREATE TABLE IF NOT EXISTS `oauth_state` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `state` VARCHAR(80) NOT NULL COMMENT 'OAuth state',
    `redirect_path` VARCHAR(255) NOT NULL COMMENT '登录后跳转路径',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_oauth_state` (`state`),
    INDEX `idx_oauth_state_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth state表';

CREATE TABLE IF NOT EXISTS `auth_email_code` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `code` VARCHAR(12) NOT NULL COMMENT '验证码',
    `purpose` VARCHAR(30) NOT NULL COMMENT '用途: LOGIN',
    `used` TINYINT(1) DEFAULT 0 COMMENT '是否已使用',
    `send_time` DATETIME NOT NULL COMMENT '发送时间',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_auth_email_code_email` (`email`),
    INDEX `idx_auth_email_code_purpose` (`purpose`),
    INDEX `idx_auth_email_code_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码表';

-- ============================================
-- 4. 创建系统设置表
-- ============================================

CREATE TABLE IF NOT EXISTS `system_settings` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `category` VARCHAR(50) NOT NULL COMMENT '配置分类',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '配置描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_category_key` (`category`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';


-- ============================================
-- 7. 插入默认配置（若不存在）
-- ============================================

INSERT IGNORE INTO `system_settings` (`category`, `config_key`, `config_value`, `description`) VALUES
('system', 'site_name', 'AI Agent', '系统名称'),
('system', 'site_logo', '', '系统Logo URL'),
('system', 'default_theme', 'light', '默认主题'),
('schedule', 'enabled', 'true', '是否启用日程功能'),
('schedule', 'storage_path', './data/schedules', '日程文件存储路径'),
('file', 'upload_dir', './data/documents', '文件上传目录'),
('file', 'allowed_types', 'txt,md,pdf,doc,docx', '允许的文件类型'),
('file', 'max_file_size', '10MB', '最大文件大小');

-- ============================================
-- 8. 派发执行：dispatched_task + push_config
-- ============================================

CREATE TABLE IF NOT EXISTS `dispatched_task` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email_id`           BIGINT       NOT NULL                COMMENT '关联邮箱配置 id',
  `email_uid`          VARCHAR(128)                         COMMENT '邮件唯一标识 (message-id 或 folder/uid)',
  `subject`            VARCHAR(512)                         COMMENT '邮件主题',
  `body_excerpt`       TEXT                                 COMMENT '邮件正文摘要',
  `importance`         VARCHAR(16)                          COMMENT '重要性: high / medium / low',
  `executor`           VARCHAR(32)  NOT NULL                COMMENT 'Python 已指定的执行器',
  `sandbox_level`      VARCHAR(32)                          COMMENT '沙箱: read-only / workspace-write / danger-full-access',
  `tool_allowlist`     JSON                                 COMMENT '工具白名单 (JSON 数组)',
  `workspace_path`     VARCHAR(1024)                        COMMENT '当前工作区绝对路径',
  `execution_instruction` MEDIUMTEXT NOT NULL               COMMENT 'Python 已生成的完整执行指令',
  `retry_max`          INT          NOT NULL DEFAULT 0      COMMENT '同一执行器的基础设施级最大重试次数',
  `executor_timeout_seconds` INT    NOT NULL                COMMENT 'Python 已指定的单次执行超时（秒）',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RUNNING / DONE / FAILED / CANCELLED',
  `retries`            INT          NOT NULL DEFAULT 0     COMMENT '当前执行器已重试次数',
  `executor_used`      VARCHAR(32)                          COMMENT '实际执行的指定执行器',
  `result`             MEDIUMTEXT                           COMMENT '执行结果 (md 内容)',
  `result_path`        VARCHAR(1024)                        COMMENT '结果 md 文件绝对路径',
  `push_status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '推送状态: pending / sent / PUSH_FAILED',
  `pushed_at`          DATETIME                             COMMENT '推送时间',
  `error_message`      TEXT                                 COMMENT '最后一次失败的错误信息',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `finished_at`        DATETIME                             COMMENT '任务结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_disp_status_created` (`status`, `created_at`),
  KEY `idx_disp_email_created`  (`email_id`, `created_at`),
  KEY `idx_disp_push_status`    (`push_status`, `pushed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派发任务主表';

CREATE TABLE IF NOT EXISTS `push_config` (
  `id`                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`                     BIGINT       NOT NULL COMMENT '所属用户ID',
  `push_email`                  VARCHAR(256)                  COMMENT '推送目标邮箱',
  `push_threshold`              VARCHAR(16)  NOT NULL DEFAULT 'medium' COMMENT '重要性阈值: high/medium/low',
  `batch_cron`                  VARCHAR(64)  NOT NULL DEFAULT '0 0 9 * * ?' COMMENT '批量推送 cron 表达式',
  `immediate_enabled`           TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用实时推送',
  `result_retention_days`       INT          NOT NULL DEFAULT 30 COMMENT '执行结果保留天数',
  `updated_at`                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_config_user` (`user_id`),
  CONSTRAINT `fk_push_config_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户派发结果推送配置';

-- ============================================
-- 定时任务执行日志（参考 xxl-job 的 xxl_job_log 设计）
-- 记录每次调度的触发时间、handler、参数、结果，用于前端调度管理页的日志面板
-- ============================================
CREATE TABLE IF NOT EXISTS `job_log` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `job_id`             BIGINT       NOT NULL                COMMENT 'scheduled_task.id',
  `job_name`           VARCHAR(100) NOT NULL                COMMENT '任务名（冗余，便于查询）',
  `handler`            VARCHAR(100) NOT NULL                COMMENT '执行的 handler 名（与 scheduled_task.task_type 对应）',
  `trigger_type`       VARCHAR(20)  NOT NULL DEFAULT 'CRON' COMMENT '触发类型: CRON / MANUAL / MISFIRE',
  `trigger_time`       DATETIME     NOT NULL                COMMENT '触发时间',
  `handle_start_time`  DATETIME                            COMMENT 'handler 实际开始执行时间',
  `handle_end_time`    DATETIME                            COMMENT 'handler 实际结束时间',
  `duration_ms`        BIGINT                              COMMENT '执行耗时（毫秒）',
  `status`             VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING / SUCCESS / FAILED',
  `executor_param`     TEXT                                COMMENT '传入 handler 的参数快照',
  `result`             MEDIUMTEXT                           COMMENT 'handler 返回的结果（截断到 4000 字符）',
  `error_message`      TEXT                                COMMENT '失败时的异常信息',
  `alarm_status`       TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0=无需告警, 1=需要告警',
  PRIMARY KEY (`id`),
  KEY `idx_log_job_id`       (`job_id`),
  KEY `idx_log_trigger_time` (`trigger_time`),
  KEY `idx_log_status`       (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';

-- 给 scheduled_task 加触发状态字段，参考 xxl-job 的 trigger_status（0=静止, 1=运行中）
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema=DATABASE() AND table_name='scheduled_task' AND column_name='trigger_status');
SET @sql := IF(@col=0,
  'ALTER TABLE `scheduled_task` ADD COLUMN `trigger_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''触发状态: 0=静止, 1=运行中'' AFTER `enabled`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 邮件附件 AI 解析（v4.1）
-- ============================================

-- email_config 增加单附件大小阈值列（每邮箱可覆盖全局默认）
SET @has_email_max_size = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'email_config'
      AND column_name = 'max_attachment_size_bytes'
);

SET @sql_email_max_size = IF(
    @has_email_max_size = 0,
    'ALTER TABLE `email_config` ADD COLUMN `max_attachment_size_bytes` BIGINT NULL COMMENT ''单附件大小阈值（字节），null 表示使用全局默认'' AFTER `provider_settings`',
    'SELECT ''email_config.max_attachment_size_bytes exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_email_max_size;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 高频查询复合索引（性能优化）
-- ============================================

-- JobTriggerThread 每秒扫 enabled=1 且 next_execute_time<=now 的任务，复合索引避免全表扫
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema=DATABASE() AND table_name='scheduled_task' AND index_name='idx_enabled_next');
SET @sql := IF(@idx=0, 'CREATE INDEX idx_enabled_next ON scheduled_task (enabled, next_execute_time)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- schedule_event 按日期范围查未完成事件
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema=DATABASE() AND table_name='schedule_event' AND index_name='idx_date_status');
SET @sql := IF(@idx=0, 'CREATE INDEX idx_date_status ON schedule_event (event_date, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- schedule_event 按提醒状态过滤未发送
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema=DATABASE() AND table_name='schedule_event' AND index_name='idx_reminder_enabled_time');
SET @sql := IF(@idx=0, 'CREATE INDEX idx_reminder_enabled_time ON schedule_event (reminder_enabled, event_time)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 邮件轮询间隔统一为 600 秒（10 分钟）
-- 仅更新现存的低频设置（< 600s），保留用户主动调高的值；NULL 留给运行时默认值兜底。
-- ============================================
UPDATE `email_config`
SET `poll_interval` = 600
WHERE `poll_interval` IS NOT NULL AND `poll_interval` < 600;

-- ============================================
-- 完成提示
-- ============================================
-- 非破坏性迁移完成！
-- 已添加/更新的功能：用户认证增强、人脸验证、OAuth、邮箱验证、搜索、系统设置、聊天导入、虚拟助手、派发执行、调度日志、邮件附件 AI 解析、AI 定时任务
-- ============================================
