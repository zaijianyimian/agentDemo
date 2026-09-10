-- =========================================================
-- agentDemo 多用户核心迁移
--
-- 目标：
-- 1. Java 仍只使用 MySQL，不引入第二数据源。
-- 2. 邮箱配置、聊天会话按 user_id 强隔离。
-- 3. chat_message 通过 chat_session 外键继承用户归属，不重复存 user_id。
-- 4. 历史单用户数据默认归属到 user_account 中最早创建的用户。
--
-- 注意：本脚本为一次性迁移脚本。执行前请备份数据库。
-- 如果已有业务数据但 user_account 为空，ALTER ... NOT NULL 会失败，这是有意的保护行为。
-- =========================================================

SET @default_user_id := (
    SELECT id
    FROM user_account
    ORDER BY id ASC
    LIMIT 1
);

-- =========================================================
-- 1. 邮箱配置多用户化
-- =========================================================

ALTER TABLE email_config
    ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

UPDATE email_config
SET user_id = @default_user_id
WHERE user_id IS NULL;

-- 原来邮箱地址全局唯一；多用户模式改为“同一用户下邮箱唯一”。
ALTER TABLE email_config
    DROP INDEX uk_email;

ALTER TABLE email_config
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID';

ALTER TABLE email_config
    ADD CONSTRAINT fk_email_config_user
        FOREIGN KEY (user_id) REFERENCES user_account(id)
            ON DELETE CASCADE;

CREATE UNIQUE INDEX uk_email_config_user_email
    ON email_config(user_id, email);

CREATE INDEX idx_email_config_user_enabled
    ON email_config(user_id, enabled);

-- 清理历史孤儿监听状态，再补上配置外键。
DELETE state
FROM email_listener_state state
LEFT JOIN email_config config ON config.id = state.config_id
WHERE config.id IS NULL;

ALTER TABLE email_listener_state
    ADD CONSTRAINT fk_email_listener_state_config
        FOREIGN KEY (config_id) REFERENCES email_config(id)
            ON DELETE CASCADE;

-- =========================================================
-- 2. 聊天会话多用户化
-- =========================================================

ALTER TABLE chat_session
    ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

UPDATE chat_session
SET user_id = @default_user_id
WHERE user_id IS NULL;

ALTER TABLE chat_session
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID';

ALTER TABLE chat_session
    ADD CONSTRAINT fk_chat_session_user
        FOREIGN KEY (user_id) REFERENCES user_account(id)
            ON DELETE CASCADE;

CREATE INDEX idx_chat_session_user_last_message
    ON chat_session(user_id, last_message_time, create_time);

-- =========================================================
-- 3. 校验建议
-- =========================================================

-- 应为 0。
SELECT COUNT(*) AS email_config_without_user
FROM email_config
WHERE user_id IS NULL;

-- 应为 0。
SELECT COUNT(*) AS chat_session_without_user
FROM chat_session
WHERE user_id IS NULL;

-- 按用户确认邮箱和会话分布。
SELECT user_id, COUNT(*) AS email_config_count
FROM email_config
GROUP BY user_id
ORDER BY user_id;

SELECT user_id, COUNT(*) AS chat_session_count
FROM chat_session
GROUP BY user_id
ORDER BY user_id;
