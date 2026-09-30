-- ============================================
-- AI 日程接入与业务幂等迁移
-- 说明:
--   1) 日程的唯一业务数据源是 MySQL schedule_event（Java 承载）。
--   2) Python Agent 不再自建 PostgreSQL schedule 副本，改由受保护的内部接口写入本表。
--   3) 本脚本只做增量扩展，不删除、不改写任何既有日程数据。
--   4) 全部语句幂等，可重复执行。
-- ============================================

SET NAMES utf8mb4;
SET @db_name = DATABASE();

-- ------------------------------------------------------------
-- 1. 结束时间。event_time 表达开始时刻，本列表达结束时刻。
--    历史数据没有真实结束时间，保持 NULL，不回填猜测值。
-- ------------------------------------------------------------
SET @has_end_time = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND column_name = 'end_time'
);
SET @sql = IF(@has_end_time = 0,
    'ALTER TABLE `schedule_event` ADD COLUMN `end_time` DATETIME NULL COMMENT ''日程结束时间（历史数据可能为空）'' AFTER `event_time`',
    'SELECT ''schedule_event.end_time exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 时区。event_time/end_time 以服务器本地时间（TZ=Asia/Shanghai）保存，
--    timezone 记录业务侧声明的 IANA 时区名，供跨时区展示还原。
-- ------------------------------------------------------------
SET @has_timezone = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND column_name = 'timezone'
);
SET @sql = IF(@has_timezone = 0,
    'ALTER TABLE `schedule_event` ADD COLUMN `timezone` VARCHAR(64) NULL COMMENT ''业务声明的 IANA 时区名，如 Asia/Shanghai'' AFTER `end_time`',
    'SELECT ''schedule_event.timezone exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. 来源类型。人工创建/聊天创建/邮件创建由业务链路决定，不由调用方随意指定。
-- ------------------------------------------------------------
SET @has_source_type = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND column_name = 'source_type'
);
SET @sql = IF(@has_source_type = 0,
    'ALTER TABLE `schedule_event` ADD COLUMN `source_type` VARCHAR(16) NOT NULL DEFAULT ''MANUAL'' COMMENT ''来源: MANUAL/CHAT/EMAIL'' AFTER `source_email`',
    'SELECT ''schedule_event.source_type exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. 来源邮件事件 UUID。
--    Python 的 email_id 是 PostgreSQL 自增主键，Java 的邮件 ID 是另一套主键，
--    两者不可互相假设相等。两侧唯一稳定的共同标识是 Java 投递时按
--    userId|provider|外部标识 派生的确定性 event_id，因此用 UUID 关联，
--    source_email_id 仅在能够确认指向 Java 本地邮件时才会被写入。
-- ------------------------------------------------------------
SET @has_source_email_event_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND column_name = 'source_email_event_id'
);
SET @sql = IF(@has_source_email_event_id = 0,
    'ALTER TABLE `schedule_event` ADD COLUMN `source_email_event_id` CHAR(36) NULL COMMENT ''来源邮件事件 UUID（跨服务稳定标识）'' AFTER `source_email_id`',
    'SELECT ''schedule_event.source_email_event_id exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 业务幂等键。
--    取值来自可信执行上下文（用户 + 会话轮次/邮件事件 + 单次工具调用），
--    不由模型生成，也不依赖标题与时间去重。
--    唯一索引让并发重试与“业务提交成功但响应丢失”都收敛到同一条记录。
--    历史数据没有幂等键，保持 NULL；MySQL 唯一索引允许多个 NULL，
--    因此不会影响既有日程。
-- ------------------------------------------------------------
SET @has_idempotency_key = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND column_name = 'idempotency_key'
);
SET @sql = IF(@has_idempotency_key = 0,
    'ALTER TABLE `schedule_event` ADD COLUMN `idempotency_key` VARCHAR(191) NULL COMMENT ''业务幂等键：同一动作重试返回同一条日程'' AFTER `user_id`',
    'SELECT ''schedule_event.idempotency_key exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND index_name = 'uk_schedule_event_user_idempotency'
);
SET @sql = IF(@has_uk = 0,
    'CREATE UNIQUE INDEX `uk_schedule_event_user_idempotency` ON `schedule_event` (`user_id`, `idempotency_key`)',
    'SELECT ''uk_schedule_event_user_idempotency exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 6. 提醒查询需要能按结束时间排序。
-- ------------------------------------------------------------
SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND index_name = 'idx_schedule_event_user_start'
);
SET @sql = IF(@has_idx = 0,
    'CREATE INDEX `idx_schedule_event_user_start` ON `schedule_event` (`user_id`, `event_time`)',
    'SELECT ''idx_schedule_event_user_start exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 7. 来源邮件事件 UUID 的检索索引（用于按邮件反查其产生的日程）。
-- ------------------------------------------------------------
SET @has_idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name AND table_name = 'schedule_event'
      AND index_name = 'idx_schedule_event_source_email_event'
);
SET @sql = IF(@has_idx = 0,
    'CREATE INDEX `idx_schedule_event_source_email_event` ON `schedule_event` (`user_id`, `source_email_event_id`)',
    'SELECT ''idx_schedule_event_source_email_event exists, skip'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================
-- 迁移完成
-- 说明：既有日程数据全部保留。历史行的 end_time/timezone/idempotency_key
-- 保持 NULL，AI 链路只对新建日程写入完整字段。
-- ============================================
