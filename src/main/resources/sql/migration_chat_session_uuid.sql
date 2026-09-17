-- ============================================
-- 聊天会话 ID UUID 迁移脚本
-- 说明:
--   1) 将 chat_session.id 从 BIGINT AUTO_INCREMENT 迁移为 CHAR(36) UUID；
--   2) 同步迁移 chat_message.session_id；
--   3) 保留旧数值 ID 到 legacy_id / legacy_session_id，便于回溯；
--   4) 本脚本仅执行一次，执行前请备份数据库。
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

START TRANSACTION;

-- 先为每个旧会话生成 UUID。
ALTER TABLE `chat_session`
    ADD COLUMN `uuid_id` CHAR(36) NULL COMMENT 'UUID 会话ID' AFTER `id`;

UPDATE `chat_session`
SET `uuid_id` = UUID()
WHERE `uuid_id` IS NULL OR `uuid_id` = '';

ALTER TABLE `chat_session`
    MODIFY COLUMN `uuid_id` CHAR(36) NOT NULL COMMENT 'UUID 会话ID';

-- 将消息关联到对应的新 UUID。
ALTER TABLE `chat_message`
    ADD COLUMN `session_uuid` CHAR(36) NULL COMMENT 'UUID 会话ID' AFTER `session_id`;

UPDATE `chat_message` AS message
JOIN `chat_session` AS session
  ON message.`session_id` = session.`id`
SET message.`session_uuid` = session.`uuid_id`;

-- 如果存在无法关联到会话的孤儿消息，停止迁移，避免生成错误关联。
SET @orphan_message_count = (
    SELECT COUNT(*)
    FROM `chat_message`
    WHERE `session_uuid` IS NULL
);

-- 将 AUTO_INCREMENT 从旧主键移除，随后交换主键列。
ALTER TABLE `chat_session`
    MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '旧会话ID';

ALTER TABLE `chat_session`
    DROP PRIMARY KEY,
    CHANGE COLUMN `id` `legacy_id` BIGINT NOT NULL COMMENT '迁移前数值会话ID',
    CHANGE COLUMN `uuid_id` `id` CHAR(36) NOT NULL COMMENT 'UUID 会话ID',
    ADD PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_chat_session_legacy_id` (`legacy_id`);

-- 保留旧 session_id 作为 legacy_session_id，新 UUID 列成为正式 session_id。
ALTER TABLE `chat_message`
    CHANGE COLUMN `session_id` `legacy_session_id` BIGINT NOT NULL COMMENT '迁移前数值会话ID',
    CHANGE COLUMN `session_uuid` `session_id` CHAR(36) NOT NULL COMMENT 'UUID 会话ID',
    ADD INDEX `idx_chat_message_session_uuid` (`session_id`),
    ADD INDEX `idx_chat_message_session_uuid_time` (`session_id`, `create_time`);

COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

SELECT @orphan_message_count AS orphan_message_count;
