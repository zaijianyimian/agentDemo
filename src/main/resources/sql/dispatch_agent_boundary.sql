-- Java Agent Runtime 剥离后的 dispatched_task 结构迁移。
-- 执行前请备份 MySQL；脚本只调整 Java dispatch 持久化字段，不访问 Python PostgreSQL。

SET @db_name := DATABASE();

-- Java 不再持有模型、Memory、RAG、Qdrant 等 Agent Runtime 配置。
DELETE FROM `system_settings`
WHERE `category` IN ('model', 'qdrant', 'memory', 'embedding', 'mcp', 'skill', 'autonomy');

DROP TABLE IF EXISTS `ai_model_config`;
DROP TABLE IF EXISTS `email_attachment_analysis`;

SET @has_executor := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'executor'
);
SET @has_executor_hint := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'executor_hint'
);
SET @sql := IF(
    @has_executor = 0 AND @has_executor_hint = 1,
    'ALTER TABLE `dispatched_task` CHANGE COLUMN `executor_hint` `executor` VARCHAR(32) NULL COMMENT ''Python 已指定的执行器''',
    'SELECT ''dispatched_task.executor exists, skip rename'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_instruction := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'execution_instruction'
);
SET @sql := IF(
    @has_instruction = 0,
    'ALTER TABLE `dispatched_task` ADD COLUMN `execution_instruction` MEDIUMTEXT NULL COMMENT ''Python 已生成的完整执行指令'' AFTER `workspace_path`',
    'SELECT ''dispatched_task.execution_instruction exists, skip'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_task_retry_max := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'retry_max'
);
SET @sql := IF(
    @has_task_retry_max = 0,
    'ALTER TABLE `dispatched_task` ADD COLUMN `retry_max` INT NOT NULL DEFAULT 0 COMMENT ''同一执行器的基础设施级最大重试次数'' AFTER `execution_instruction`',
    'SELECT ''dispatched_task.retry_max exists, skip'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 旧任务没有 Python 生成的完整指令，不允许继续按旧 Java Agent 链路运行。
UPDATE `dispatched_task`
SET `status` = 'FAILED',
    `error_message` = 'Task predates Python execution request; redispatch from Python Agent',
    `finished_at` = COALESCE(`finished_at`, NOW()),
    `updated_at` = NOW()
WHERE `status` IN ('PENDING', 'RUNNING')
  AND (`execution_instruction` IS NULL OR `execution_instruction` = '');

UPDATE `dispatched_task`
SET `execution_instruction` = ''
WHERE `execution_instruction` IS NULL;

UPDATE `dispatched_task`
SET `executor` = 'unknown'
WHERE `executor` IS NULL OR `executor` = '';

ALTER TABLE `dispatched_task`
    MODIFY COLUMN `executor` VARCHAR(32) NOT NULL COMMENT 'Python 已指定的执行器',
    MODIFY COLUMN `execution_instruction` MEDIUMTEXT NOT NULL COMMENT 'Python 已生成的完整执行指令';

SET @has_fallback_executor := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'fallback_executor'
);
SET @sql := IF(@has_fallback_executor = 1,
    'ALTER TABLE `dispatched_task` DROP COLUMN `fallback_executor`',
    'SELECT ''fallback_executor absent, skip'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_agent_default_hint := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'email_config' AND column_name = 'agent_default_hint'
);
SET @sql := IF(@has_agent_default_hint = 1,
    'ALTER TABLE `email_config` DROP COLUMN `agent_default_hint`',
    'SELECT ''email_config.agent_default_hint absent, skip'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_user_hint := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'user_hint'
);
SET @sql := IF(@has_user_hint = 1,
    'ALTER TABLE `dispatched_task` DROP COLUMN `user_hint`',
    'SELECT ''user_hint absent, skip'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_final_hint := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'dispatched_task' AND column_name = 'final_hint'
);
SET @sql := IF(@has_final_hint = 1,
    'ALTER TABLE `dispatched_task` DROP COLUMN `final_hint`',
    'SELECT ''final_hint absent, skip'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_global_retry_max := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'push_config' AND column_name = 'retry_max'
);
SET @sql := IF(@has_global_retry_max = 1,
    'ALTER TABLE `push_config` DROP COLUMN `retry_max`',
    'SELECT ''push_config.retry_max absent, skip'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
