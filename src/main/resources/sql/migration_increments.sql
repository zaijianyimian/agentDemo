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
-- 3. 创建搜索相关表
-- ============================================

CREATE TABLE IF NOT EXISTS `search_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `query` VARCHAR(500) NOT NULL COMMENT '搜索关键词',
    `search_mode` VARCHAR(20) DEFAULT 'normal' COMMENT '搜索模式: normal/summary/stream',
    `result_count` INT DEFAULT 0 COMMENT '搜索结果数量',
    `has_summary` BOOLEAN DEFAULT FALSE COMMENT '是否有AI总结',
    `duration_ms` BIGINT COMMENT '搜索耗时（毫秒）',
    `session_id` VARCHAR(100) COMMENT '用户会话ID',
    `source_ip` VARCHAR(50) COMMENT '搜索来源IP',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

CREATE TABLE IF NOT EXISTS `user_interest` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tag` VARCHAR(100) NOT NULL COMMENT '兴趣标签名称',
    `category` VARCHAR(50) DEFAULT 'other' COMMENT '兴趣分类',
    `weight` INT DEFAULT 1 COMMENT '兴趣权重',
    `related_keywords` TEXT COMMENT '相关搜索关键词（JSON数组）',
    `last_search_time` DATETIME COMMENT '最后搜索时间',
    `search_count` INT DEFAULT 1 COMMENT '搜索次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_tag` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣表';

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
-- 5. 创建聊天记录导入与虚拟助手表
-- ============================================

CREATE TABLE IF NOT EXISTS `chat_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID（同一对话的记录分组）',
    `platform` VARCHAR(30) NOT NULL COMMENT '来源平台: wechat/qq/telegram/whatsapp/other',
    `sender` VARCHAR(100) COMMENT '发送者名称',
    `sender_type` VARCHAR(20) NOT NULL COMMENT '发送者类型: user/assistant/system',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(20) DEFAULT 'text' COMMENT '消息类型: text/media/system',
    `media_type` VARCHAR(20) COMMENT '媒体类型: image/audio/video/file/sticker',
    `media_name` VARCHAR(255) COMMENT '媒体文件名或描述',
    `message_time` DATETIME COMMENT '消息时间',
    `assistant_id` BIGINT COMMENT '关联的虚拟助手ID（如果已训练）',
    `vectorized` TINYINT(1) DEFAULT 0 COMMENT '是否已向量化',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_platform` (`platform`),
    INDEX `idx_assistant_id` (`assistant_id`),
    INDEX `idx_vectorized` (`vectorized`),
    INDEX `idx_message_time` (`message_time`),
    INDEX `idx_message_type` (`message_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天记录表';

-- 添加 chat_history 媒体字段（增量迁移）
SET @has_message_type = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'chat_history'
      AND column_name = 'message_type'
);

SET @sql_message_type = IF(
    @has_message_type = 0,
    'ALTER TABLE `chat_history` ADD COLUMN `message_type` VARCHAR(20) DEFAULT ''text'' COMMENT ''消息类型: text/media/system'' AFTER `content`',
    'SELECT ''chat_history.message_type exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_message_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_media_type = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'chat_history'
      AND column_name = 'media_type'
);

SET @sql_media_type = IF(
    @has_media_type = 0,
    'ALTER TABLE `chat_history` ADD COLUMN `media_type` VARCHAR(20) COMMENT ''媒体类型: image/audio/video/file/sticker'' AFTER `message_type`',
    'SELECT ''chat_history.media_type exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_media_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_media_name = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'chat_history'
      AND column_name = 'media_name'
);

SET @sql_media_name = IF(
    @has_media_name = 0,
    'ALTER TABLE `chat_history` ADD COLUMN `media_name` VARCHAR(255) COMMENT ''媒体文件名或描述'' AFTER `media_type`',
    'SELECT ''chat_history.media_name exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_media_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `virtual_assistant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '助手名称',
    `description` VARCHAR(500) COMMENT '助手描述',
    `source_platform` VARCHAR(30) NOT NULL COMMENT '来源平台（训练数据来源）',
    `trained_messages` INT DEFAULT 0 COMMENT '训练的消息数量',
    `collection_name` VARCHAR(100) NOT NULL COMMENT '关联的向量集合名称',
    `personality_summary` TEXT COMMENT '人格描述（AI生成的摘要）',
    `topics` VARCHAR(500) COMMENT '常用话题标签',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_collection_name` (`collection_name`),
    INDEX `idx_enabled` (`enabled`),
    INDEX `idx_platform` (`source_platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟助手表';

-- ============================================
-- 6. 创建缺失的索引
-- ============================================

-- 搜索历史索引
SET @has_idx_search_time = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'search_history'
      AND index_name = 'idx_search_history_create_time'
);

SET @sql_idx_search_time = IF(
    @has_idx_search_time = 0,
    'CREATE INDEX `idx_search_history_create_time` ON `search_history`(`create_time`)',
    'SELECT ''idx_search_history_create_time exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_idx_search_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_search_query = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'search_history'
      AND index_name = 'idx_search_history_query'
);

SET @sql_idx_search_query = IF(
    @has_idx_search_query = 0,
    'CREATE INDEX `idx_search_history_query` ON `search_history`(`query`)',
    'SELECT ''idx_search_history_query exists, skip'' AS msg'
);
PREPARE stmt FROM @sql_idx_search_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 7. 插入默认配置（若不存在）
-- ============================================

INSERT IGNORE INTO `system_settings` (`category`, `config_key`, `config_value`, `description`) VALUES
('system', 'site_name', 'AI Agent', '系统名称'),
('system', 'site_logo', '', '系统Logo URL'),
('system', 'default_theme', 'light', '默认主题'),
('model', 'temperature', '0.7', '模型温度'),
('model', 'maxTokens', '4096', '最大Token数'),
('model', 'topP', '0.9', 'Top P 核采样'),
('model', 'memorySize', '20', '上下文记忆数量'),
('model', 'systemPrompt', '你是一个有帮助的AI助手，请用简洁、准确的语言回答问题。', '系统提示词'),
('qdrant', 'host', 'localhost', 'Qdrant 服务地址'),
('qdrant', 'port', '6334', 'Qdrant 服务端口'),
('qdrant', 'collection_name', 'agent_memory', '向量集合名称'),
('qdrant', 'top_k', '5', '返回结果数量'),
('qdrant', 'min_score', '0.5', '最小相似度分数'),
('search', 'enabled', 'true', '是否启用搜索'),
('search', 'engine', 'serper', '搜索引擎'),
('search', 'max_results', '3', '最大搜索结果数'),
('schedule', 'enabled', 'true', '是否启用日程功能'),
('schedule', 'storage_path', './data/schedules', '日程文件存储路径'),
('file', 'upload_dir', './data/documents', '文件上传目录'),
('file', 'allowed_types', 'txt,md,pdf,doc,docx', '允许的文件类型'),
('file', 'max_file_size', '10MB', '最大文件大小');

-- ============================================
-- 完成提示
-- ============================================
-- 非破坏性迁移完成！
-- 已添加/更新的功能：用户认证增强、人脸验证、OAuth、邮箱验证、搜索、系统设置、聊天导入、虚拟助手
-- ============================================