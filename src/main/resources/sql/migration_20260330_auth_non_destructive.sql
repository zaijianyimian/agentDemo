-- ============================================
-- 非破坏性迁移脚本（认证安全链路）
-- 生成时间: 2026-03-30
-- 说明:
-- 1) 不执行 DROP TABLE / DROP COLUMN
-- 2) 保留历史数据
-- 3) 兼容旧版 oauth_account 仍有 access_token 列的情况
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- 使用当前数据库
SET @db_name = DATABASE();

-- ============================================
-- 1) user_account 增加 token_version（若不存在）
-- ============================================
SET @has_user_account = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @db_name
      AND table_name = 'user_account'
);

SET @sql_user_account = IF(
    @has_user_account = 0,
    'CREATE TABLE `user_account` (
        `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT ''主键ID'',
        `username` VARCHAR(50) NOT NULL COMMENT ''登录名'',
        `email` VARCHAR(100) NOT NULL COMMENT ''邮箱'',
        `password_hash` VARCHAR(255) NOT NULL COMMENT ''BCrypt密码哈希'',
        `display_name` VARCHAR(100) DEFAULT NULL COMMENT ''显示名'',
        `role` VARCHAR(30) DEFAULT ''USER'' COMMENT ''角色'',
        `enabled` TINYINT(1) DEFAULT 1 COMMENT ''是否启用'',
        `email_verified` TINYINT(1) DEFAULT 0 COMMENT ''邮箱是否已验证'',
        `token_version` INT NOT NULL DEFAULT 0 COMMENT ''JWT版本号，用于令牌失效'',
        `last_login_time` DATETIME DEFAULT NULL COMMENT ''最后登录时间'',
        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
        UNIQUE KEY `uk_user_account_username` (`username`),
        UNIQUE KEY `uk_user_account_email` (`email`),
        INDEX `idx_user_account_enabled` (`enabled`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''用户账号表''',
    'SELECT ''user_account exists, skip create'' AS msg'
);
PREPARE stmt_user_account FROM @sql_user_account;
EXECUTE stmt_user_account;
DEALLOCATE PREPARE stmt_user_account;

SET @has_token_version = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'user_account'
      AND column_name = 'token_version'
);

SET @sql_token_version = IF(
    @has_token_version = 0,
    'ALTER TABLE `user_account`
       ADD COLUMN `token_version` INT NOT NULL DEFAULT 0 COMMENT ''JWT版本号，用于令牌失效'' AFTER `email_verified`',
    'SELECT ''user_account.token_version exists, skip add'' AS msg'
);
PREPARE stmt_token_version FROM @sql_token_version;
EXECUTE stmt_token_version;
DEALLOCATE PREPARE stmt_token_version;

-- 兜底：把历史空值修正为 0
UPDATE `user_account`
SET `token_version` = 0
WHERE `token_version` IS NULL;

-- ============================================
-- 2) oauth_account 表（若不存在则创建）
-- 说明: 保留 access_token 列，不做 DROP COLUMN，避免数据损失
-- ============================================
CREATE TABLE IF NOT EXISTS `oauth_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
    `provider` VARCHAR(30) NOT NULL COMMENT '提供商: github',
    `provider_user_id` VARCHAR(100) NOT NULL COMMENT '第三方用户ID',
    `login` VARCHAR(100) DEFAULT NULL COMMENT '第三方登录名',
    `access_token` TEXT DEFAULT NULL COMMENT '历史兼容字段，建议后续停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_oauth_provider_user` (`provider`, `provider_user_id`),
    INDEX `idx_oauth_user_id` (`user_id`),
    CONSTRAINT `fk_oauth_account_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方账号绑定表';

-- 若 oauth_account 已存在，补齐关键索引与外键（非破坏）
SET @has_idx_oauth_provider_user = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'oauth_account'
      AND index_name = 'uk_oauth_provider_user'
);

SET @sql_idx_oauth_provider_user = IF(
    @has_idx_oauth_provider_user = 0,
    'ALTER TABLE `oauth_account` ADD UNIQUE INDEX `uk_oauth_provider_user` (`provider`, `provider_user_id`)',
    'SELECT ''oauth_account.uk_oauth_provider_user exists, skip add'' AS msg'
);
PREPARE stmt_idx_oauth_provider_user FROM @sql_idx_oauth_provider_user;
EXECUTE stmt_idx_oauth_provider_user;
DEALLOCATE PREPARE stmt_idx_oauth_provider_user;

SET @has_idx_oauth_user_id = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'oauth_account'
      AND index_name = 'idx_oauth_user_id'
);

SET @sql_idx_oauth_user_id = IF(
    @has_idx_oauth_user_id = 0,
    'ALTER TABLE `oauth_account` ADD INDEX `idx_oauth_user_id` (`user_id`)',
    'SELECT ''oauth_account.idx_oauth_user_id exists, skip add'' AS msg'
);
PREPARE stmt_idx_oauth_user_id FROM @sql_idx_oauth_user_id;
EXECUTE stmt_idx_oauth_user_id;
DEALLOCATE PREPARE stmt_idx_oauth_user_id;

SET @has_fk_oauth_user = (
    SELECT COUNT(*)
    FROM information_schema.key_column_usage
    WHERE table_schema = @db_name
      AND table_name = 'oauth_account'
      AND column_name = 'user_id'
      AND referenced_table_schema = @db_name
      AND referenced_table_name = 'user_account'
      AND referenced_column_name = 'id'
);

SET @sql_fk_oauth_user = IF(
    @has_fk_oauth_user = 0,
    'ALTER TABLE `oauth_account` ADD CONSTRAINT `fk_oauth_account_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE',
    'SELECT ''oauth_account fk(user_id->user_account.id) exists, skip add'' AS msg'
);
PREPARE stmt_fk_oauth_user FROM @sql_fk_oauth_user;
EXECUTE stmt_fk_oauth_user;
DEALLOCATE PREPARE stmt_fk_oauth_user;

-- ============================================
-- 3) oauth_state 表（新增）
-- ============================================
CREATE TABLE IF NOT EXISTS `oauth_state` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `state` VARCHAR(80) NOT NULL COMMENT 'OAuth state',
    `redirect_path` VARCHAR(255) NOT NULL COMMENT '登录后跳转路径',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_oauth_state` (`state`),
    INDEX `idx_oauth_state_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth state表';

-- ============================================
-- 4) auth_email_code 表（若不存在则创建）
-- ============================================
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
-- 5) 可选清理（默认不执行）
-- 如果你确认不再需要历史 OAuth access_token，可手动执行:
-- ALTER TABLE `oauth_account` DROP COLUMN `access_token`;
-- ============================================
