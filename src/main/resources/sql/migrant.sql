-- =========================================================
-- agentDemo 多用户完整迁移脚本
-- 版本: 2026-09-10
--
-- 适用场景：已有单用户 MySQL 数据库升级到 feat/graph-gateway-multi-user。
-- 设计原则：
-- 1. Java 仍然只连接 MySQL；Python Graph 独占 PostgreSQL。
-- 2. 用户私有业务表统一增加 user_id，并由 MyBatis TenantLine 强制隔离。
-- 3. chat_message / email_listener_state / skill_tool_mapping 等子表通过父表继承归属，不重复增加 user_id。
-- 4. skill / mcp_tool 支持系统记录(user_id IS NULL) + 用户私有记录(user_id = 当前用户)。
-- 5. 历史单用户数据默认归属 user_account 中最早创建的用户。
-- 6. 本脚本可重复执行；执行前仍建议备份数据库。
-- =========================================================

SET NAMES utf8mb4;
SET @db_name := DATABASE();
SET FOREIGN_KEY_CHECKS = 0;

SET @default_user_id := (
    SELECT id
    FROM user_account
    ORDER BY id ASC
    LIMIT 1
);

DELIMITER $$

DROP PROCEDURE IF EXISTS `mig_add_column`$$
CREATE PROCEDURE `mig_add_column`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @mig_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'),
            '` ADD COLUMN `', REPLACE(p_column, '`', '``'), '` ', p_definition
        );
        PREPARE mig_stmt FROM @mig_sql;
        EXECUTE mig_stmt;
        DEALLOCATE PREPARE mig_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `mig_add_index`$$
CREATE PROCEDURE `mig_add_index`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @mig_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` ADD ', p_definition
        );
        PREPARE mig_stmt FROM @mig_sql;
        EXECUTE mig_stmt;
        DEALLOCATE PREPARE mig_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `mig_drop_index`$$
CREATE PROCEDURE `mig_drop_index`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @mig_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'),
            '` DROP INDEX `', REPLACE(p_index, '`', '``'), '`'
        );
        PREPARE mig_stmt FROM @mig_sql;
        EXECUTE mig_stmt;
        DEALLOCATE PREPARE mig_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `mig_add_fk`$$
CREATE PROCEDURE `mig_add_fk`(
    IN p_table VARCHAR(64),
    IN p_constraint VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = p_table
          AND constraint_name = p_constraint
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        SET @mig_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` ADD ', p_definition
        );
        PREPARE mig_stmt FROM @mig_sql;
        EXECUTE mig_stmt;
        DEALLOCATE PREPARE mig_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `mig_require_user_not_null`$$
CREATE PROCEDURE `mig_require_user_not_null`(IN p_table VARCHAR(64))
BEGIN
    DECLARE v_nullable_count INT DEFAULT 0;
    DECLARE v_message VARCHAR(255);

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = 'user_id'
    ) THEN
        SET @mig_null_count = 0;
        SET @mig_sql = CONCAT(
            'SELECT COUNT(*) INTO @mig_null_count FROM `',
            REPLACE(p_table, '`', '``'), '` WHERE `user_id` IS NULL'
        );
        PREPARE mig_stmt FROM @mig_sql;
        EXECUTE mig_stmt;
        DEALLOCATE PREPARE mig_stmt;

        IF COALESCE(@mig_null_count, 0) > 0 THEN
            SET v_message = CONCAT(
                'migration aborted: ', p_table,
                ' still contains rows without user_id; create a user_account or repair ownership first'
            );
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
        END IF;

        SELECT COUNT(*) INTO v_nullable_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = 'user_id'
          AND is_nullable = 'YES';

        IF v_nullable_count > 0 THEN
            SET @mig_sql = CONCAT(
                'ALTER TABLE `', REPLACE(p_table, '`', '``'),
                '` MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT ''所属用户ID'''
            );
            PREPARE mig_stmt FROM @mig_sql;
            EXECUTE mig_stmt;
            DEALLOCATE PREPARE mig_stmt;
        END IF;
    END IF;
END$$

DELIMITER ;

-- =========================================================
-- 1. 补齐当前 Java 实体需要的表 / 字段
-- =========================================================

CREATE TABLE IF NOT EXISTS `job_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NULL COMMENT '所属用户ID',
    `job_id` BIGINT NOT NULL COMMENT 'scheduled_task.id',
    `job_name` VARCHAR(100) NOT NULL COMMENT '任务名',
    `handler` VARCHAR(100) NOT NULL COMMENT '执行 handler',
    `trigger_type` VARCHAR(20) NOT NULL DEFAULT 'CRON' COMMENT 'CRON/MANUAL/MISFIRE',
    `trigger_time` DATETIME NOT NULL COMMENT '触发时间',
    `handle_start_time` DATETIME DEFAULT NULL COMMENT '执行开始时间',
    `handle_end_time` DATETIME DEFAULT NULL COMMENT '执行结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时毫秒',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    `executor_param` TEXT COMMENT '执行参数快照',
    `result` MEDIUMTEXT COMMENT '执行结果',
    `error_message` TEXT COMMENT '失败信息',
    `alarm_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=无需告警,1=需要告警',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_job_id` (`job_id`),
    KEY `idx_log_trigger_time` (`trigger_time`),
    KEY `idx_log_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行日志';

CALL mig_add_column('scheduled_task', 'trigger_status',
    'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''触发状态: 0=静止, 1=运行中'' AFTER `enabled`');
CALL mig_add_column('scheduled_task', 'requires_ai',
    'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否走 AI 执行路径'' AFTER `trigger_status`');
CALL mig_add_column('job_log', 'create_time',
    'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''');

-- =========================================================
-- 2. 所有用户归属字段
-- =========================================================

-- 强隔离表：最终会收紧为 NOT NULL。
CALL mig_add_column('email_config', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('email_attachment_analysis', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('email_attachment_analysis', 'email_config_id',
    'BIGINT NULL COMMENT ''所属邮箱配置ID'' AFTER `user_id`');
CALL mig_add_column('chat_session', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('schedule_event', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('scheduled_task', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('job_log', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('note', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('code_snippet', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('document', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('chat_history', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('virtual_assistant', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('knowledge_base', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('knowledge_document', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('search_history', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('user_interest', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('dispatched_task', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');
CALL mig_add_column('ai_model_config', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID'' AFTER `id`');

-- 混合范围表：NULL 表示系统内置记录，因此保持可空。
CALL mig_add_column('mcp_tool', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID，NULL=系统工具'' AFTER `id`');
CALL mig_add_column('skill', 'user_id', 'BIGINT NULL COMMENT ''所属用户ID，NULL=系统内置技能'' AFTER `id`');

-- =========================================================
-- 3. 历史数据归属回填
-- =========================================================

UPDATE `email_config`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `chat_session`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `schedule_event`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `scheduled_task`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `note`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `code_snippet`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `document`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `virtual_assistant`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `knowledge_base`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `search_history`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `user_interest`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `ai_model_config`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

-- 子业务表优先从父记录继承 owner，再回退到默认用户。
UPDATE `email_attachment_analysis` analysis
JOIN `email_config` config ON config.id = analysis.email_config_id
SET analysis.user_id = config.user_id
WHERE analysis.user_id IS NULL
  AND config.user_id IS NOT NULL;

UPDATE `email_attachment_analysis` analysis
JOIN `email_config` config ON config.email = analysis.account_email
SET analysis.email_config_id = config.id,
    analysis.user_id = COALESCE(analysis.user_id, config.user_id)
WHERE analysis.email_config_id IS NULL
  AND analysis.account_email IS NOT NULL;

UPDATE `email_attachment_analysis`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `dispatched_task` task
JOIN `email_config` config ON config.id = task.email_id
SET task.user_id = config.user_id
WHERE task.user_id IS NULL
  AND config.user_id IS NOT NULL;

UPDATE `dispatched_task`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `job_log` log_row
JOIN `scheduled_task` task ON task.id = log_row.job_id
SET log_row.user_id = task.user_id
WHERE log_row.user_id IS NULL
  AND task.user_id IS NOT NULL;

UPDATE `job_log`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `knowledge_document` doc
JOIN `knowledge_base` base ON base.id = doc.base_id
SET doc.user_id = base.user_id
WHERE doc.user_id IS NULL
  AND base.user_id IS NOT NULL;

UPDATE `knowledge_document`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

UPDATE `chat_history` history_row
JOIN `virtual_assistant` assistant ON assistant.id = history_row.assistant_id
SET history_row.user_id = assistant.user_id
WHERE history_row.user_id IS NULL
  AND assistant.user_id IS NOT NULL;

UPDATE `chat_history`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL AND @default_user_id IS NOT NULL;

-- Skill：内置技能继续保持 user_id=NULL；历史自定义技能归属默认用户。
UPDATE `skill`
SET `user_id` = @default_user_id
WHERE `user_id` IS NULL
  AND COALESCE(`is_builtin`, 0) = 0
  AND @default_user_id IS NOT NULL;

-- MCP Tool：被系统内置 Skill 引用的工具保持全局，其余历史工具归属默认用户。
UPDATE `mcp_tool` tool
LEFT JOIN (
    SELECT DISTINCT mapping.tool_id
    FROM `skill_tool_mapping` mapping
    JOIN `skill` builtin_skill ON builtin_skill.id = mapping.skill_id
    WHERE builtin_skill.user_id IS NULL
      AND builtin_skill.is_builtin = 1
) builtin_tool ON builtin_tool.tool_id = tool.id
SET tool.user_id = @default_user_id
WHERE tool.user_id IS NULL
  AND builtin_tool.tool_id IS NULL
  AND @default_user_id IS NOT NULL;

-- =========================================================
-- 4. 私有表收紧为 NOT NULL
-- =========================================================

CALL mig_require_user_not_null('email_config');
CALL mig_require_user_not_null('email_attachment_analysis');
CALL mig_require_user_not_null('chat_session');
CALL mig_require_user_not_null('schedule_event');
CALL mig_require_user_not_null('scheduled_task');
CALL mig_require_user_not_null('job_log');
CALL mig_require_user_not_null('note');
CALL mig_require_user_not_null('code_snippet');
CALL mig_require_user_not_null('document');
CALL mig_require_user_not_null('chat_history');
CALL mig_require_user_not_null('virtual_assistant');
CALL mig_require_user_not_null('knowledge_base');
CALL mig_require_user_not_null('knowledge_document');
CALL mig_require_user_not_null('search_history');
CALL mig_require_user_not_null('user_interest');
CALL mig_require_user_not_null('dispatched_task');
CALL mig_require_user_not_null('ai_model_config');

-- =========================================================
-- 5. 单用户唯一约束 -> owner 维度唯一约束
-- =========================================================

CALL mig_drop_index('email_config', 'uk_email');
CALL mig_drop_index('user_interest', 'uk_tag');
CALL mig_drop_index('virtual_assistant', 'uk_collection_name');
CALL mig_drop_index('mcp_tool', 'uk_name');
CALL mig_drop_index('skill', 'uk_code');

CALL mig_add_index('email_config', 'uk_email_config_user_email',
    'UNIQUE INDEX `uk_email_config_user_email` (`user_id`, `email`)');
CALL mig_add_index('user_interest', 'uk_user_interest_user_tag',
    'UNIQUE INDEX `uk_user_interest_user_tag` (`user_id`, `tag`)');
CALL mig_add_index('virtual_assistant', 'uk_virtual_assistant_user_collection',
    'UNIQUE INDEX `uk_virtual_assistant_user_collection` (`user_id`, `collection_name`)');
CALL mig_add_index('mcp_tool', 'uk_mcp_tool_user_name',
    'UNIQUE INDEX `uk_mcp_tool_user_name` (`user_id`, `name`)');
CALL mig_add_index('skill', 'uk_skill_user_code',
    'UNIQUE INDEX `uk_skill_user_code` (`user_id`, `code`)');

-- =========================================================
-- 6. 多用户高频查询索引
-- =========================================================

CALL mig_add_index('email_config', 'idx_email_config_user_enabled',
    'INDEX `idx_email_config_user_enabled` (`user_id`, `enabled`)');
CALL mig_add_index('email_attachment_analysis', 'idx_eaa_user_message_file',
    'INDEX `idx_eaa_user_message_file` (`user_id`, `email_config_id`, `message_id`(128), `file_name`(128))');
CALL mig_add_index('chat_session', 'idx_chat_session_user_last_message',
    'INDEX `idx_chat_session_user_last_message` (`user_id`, `last_message_time`, `create_time`)');
CALL mig_add_index('schedule_event', 'idx_schedule_user_date_status',
    'INDEX `idx_schedule_user_date_status` (`user_id`, `event_date`, `status`)');
CALL mig_add_index('scheduled_task', 'idx_task_user_enabled_next',
    'INDEX `idx_task_user_enabled_next` (`user_id`, `enabled`, `next_execute_time`)');
CALL mig_add_index('job_log', 'idx_job_log_user_job_create',
    'INDEX `idx_job_log_user_job_create` (`user_id`, `job_id`, `create_time`)');
CALL mig_add_index('note', 'idx_note_user_pinned_update',
    'INDEX `idx_note_user_pinned_update` (`user_id`, `is_pinned`, `update_time`)');
CALL mig_add_index('code_snippet', 'idx_snippet_user_language',
    'INDEX `idx_snippet_user_language` (`user_id`, `language`)');
CALL mig_add_index('document', 'idx_document_user_status_create',
    'INDEX `idx_document_user_status_create` (`user_id`, `status`, `create_time`)');
CALL mig_add_index('chat_history', 'idx_chat_history_user_session_time',
    'INDEX `idx_chat_history_user_session_time` (`user_id`, `session_id`, `message_time`)');
CALL mig_add_index('virtual_assistant', 'idx_virtual_assistant_user_enabled',
    'INDEX `idx_virtual_assistant_user_enabled` (`user_id`, `enabled`)');
CALL mig_add_index('knowledge_base', 'idx_knowledge_base_user_enabled',
    'INDEX `idx_knowledge_base_user_enabled` (`user_id`, `enabled`)');
CALL mig_add_index('knowledge_document', 'idx_knowledge_document_user_base_status',
    'INDEX `idx_knowledge_document_user_base_status` (`user_id`, `base_id`, `status`)');
CALL mig_add_index('search_history', 'idx_search_history_user_create',
    'INDEX `idx_search_history_user_create` (`user_id`, `create_time`)');
CALL mig_add_index('dispatched_task', 'idx_dispatch_user_status_created',
    'INDEX `idx_dispatch_user_status_created` (`user_id`, `status`, `created_at`)');
CALL mig_add_index('ai_model_config', 'idx_model_user_enabled_default',
    'INDEX `idx_model_user_enabled_default` (`user_id`, `enabled`, `is_default`)');
CALL mig_add_index('mcp_tool', 'idx_mcp_tool_user_enabled',
    'INDEX `idx_mcp_tool_user_enabled` (`user_id`, `enabled`)');
CALL mig_add_index('skill', 'idx_skill_user_enabled_category',
    'INDEX `idx_skill_user_enabled_category` (`user_id`, `enabled`, `category`)');

-- =========================================================
-- 7. 用户归属外键
-- =========================================================

CALL mig_add_fk('email_config', 'fk_email_config_user',
    'CONSTRAINT `fk_email_config_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('email_attachment_analysis', 'fk_eaa_user',
    'CONSTRAINT `fk_eaa_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('email_attachment_analysis', 'fk_eaa_email_config',
    'CONSTRAINT `fk_eaa_email_config` FOREIGN KEY (`email_config_id`) REFERENCES `email_config`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('chat_session', 'fk_chat_session_user',
    'CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('schedule_event', 'fk_schedule_event_user',
    'CONSTRAINT `fk_schedule_event_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('scheduled_task', 'fk_scheduled_task_user',
    'CONSTRAINT `fk_scheduled_task_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('job_log', 'fk_job_log_user',
    'CONSTRAINT `fk_job_log_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('job_log', 'fk_job_log_task',
    'CONSTRAINT `fk_job_log_task` FOREIGN KEY (`job_id`) REFERENCES `scheduled_task`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('note', 'fk_note_user',
    'CONSTRAINT `fk_note_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('code_snippet', 'fk_code_snippet_user',
    'CONSTRAINT `fk_code_snippet_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('document', 'fk_document_user',
    'CONSTRAINT `fk_document_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('chat_history', 'fk_chat_history_user',
    'CONSTRAINT `fk_chat_history_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('virtual_assistant', 'fk_virtual_assistant_user',
    'CONSTRAINT `fk_virtual_assistant_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('knowledge_base', 'fk_knowledge_base_user',
    'CONSTRAINT `fk_knowledge_base_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('knowledge_document', 'fk_knowledge_document_user',
    'CONSTRAINT `fk_knowledge_document_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('search_history', 'fk_search_history_user',
    'CONSTRAINT `fk_search_history_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('user_interest', 'fk_user_interest_user',
    'CONSTRAINT `fk_user_interest_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('dispatched_task', 'fk_dispatched_task_user',
    'CONSTRAINT `fk_dispatched_task_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('ai_model_config', 'fk_ai_model_config_user',
    'CONSTRAINT `fk_ai_model_config_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('mcp_tool', 'fk_mcp_tool_user',
    'CONSTRAINT `fk_mcp_tool_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');
CALL mig_add_fk('skill', 'fk_skill_user',
    'CONSTRAINT `fk_skill_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE');

DELETE state_row
FROM `email_listener_state` state_row
LEFT JOIN `email_config` config ON config.id = state_row.config_id
WHERE config.id IS NULL;

CALL mig_add_fk('email_listener_state', 'fk_email_listener_state_config',
    'CONSTRAINT `fk_email_listener_state_config` FOREIGN KEY (`config_id`) REFERENCES `email_config`(`id`) ON DELETE CASCADE');

-- =========================================================
-- 8. 校验
-- =========================================================

SELECT 'email_config' AS table_name, COUNT(*) AS rows_without_user FROM `email_config` WHERE user_id IS NULL
UNION ALL SELECT 'email_attachment_analysis', COUNT(*) FROM `email_attachment_analysis` WHERE user_id IS NULL
UNION ALL SELECT 'chat_session', COUNT(*) FROM `chat_session` WHERE user_id IS NULL
UNION ALL SELECT 'schedule_event', COUNT(*) FROM `schedule_event` WHERE user_id IS NULL
UNION ALL SELECT 'scheduled_task', COUNT(*) FROM `scheduled_task` WHERE user_id IS NULL
UNION ALL SELECT 'job_log', COUNT(*) FROM `job_log` WHERE user_id IS NULL
UNION ALL SELECT 'note', COUNT(*) FROM `note` WHERE user_id IS NULL
UNION ALL SELECT 'code_snippet', COUNT(*) FROM `code_snippet` WHERE user_id IS NULL
UNION ALL SELECT 'document', COUNT(*) FROM `document` WHERE user_id IS NULL
UNION ALL SELECT 'chat_history', COUNT(*) FROM `chat_history` WHERE user_id IS NULL
UNION ALL SELECT 'virtual_assistant', COUNT(*) FROM `virtual_assistant` WHERE user_id IS NULL
UNION ALL SELECT 'knowledge_base', COUNT(*) FROM `knowledge_base` WHERE user_id IS NULL
UNION ALL SELECT 'knowledge_document', COUNT(*) FROM `knowledge_document` WHERE user_id IS NULL
UNION ALL SELECT 'search_history', COUNT(*) FROM `search_history` WHERE user_id IS NULL
UNION ALL SELECT 'user_interest', COUNT(*) FROM `user_interest` WHERE user_id IS NULL
UNION ALL SELECT 'dispatched_task', COUNT(*) FROM `dispatched_task` WHERE user_id IS NULL
UNION ALL SELECT 'ai_model_config', COUNT(*) FROM `ai_model_config` WHERE user_id IS NULL;

SELECT user_id, COUNT(*) AS email_config_count
FROM `email_config`
GROUP BY user_id
ORDER BY user_id;

SELECT user_id, COUNT(*) AS scheduled_task_count
FROM `scheduled_task`
GROUP BY user_id
ORDER BY user_id;

SELECT user_id, is_builtin, COUNT(*) AS skill_count
FROM `skill`
GROUP BY user_id, is_builtin
ORDER BY user_id, is_builtin;

-- =========================================================
-- 9. 清理迁移辅助过程
-- =========================================================

DROP PROCEDURE IF EXISTS `mig_add_column`;
DROP PROCEDURE IF EXISTS `mig_add_index`;
DROP PROCEDURE IF EXISTS `mig_drop_index`;
DROP PROCEDURE IF EXISTS `mig_add_fk`;
DROP PROCEDURE IF EXISTS `mig_require_user_not_null`;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 迁移完成
-- =========================================================
