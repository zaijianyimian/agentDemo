-- ============================================
-- AI Agent 数据库初始化脚本
-- 创建时间: 2026-03-29
-- 说明: 合并所有模块的数据库表结构
-- 注意: 本脚本为非破坏性初始化，不会自动 DROP 现有表
-- 如需重置数据库，请在开发环境手动执行 DROP 后再运行本脚本
-- ============================================

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. AI 模型配置表
-- ============================================
CREATE TABLE IF NOT EXISTS `ai_model_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '模型名称(显示用)',
    `provider` VARCHAR(50) NOT NULL COMMENT '提供商: openai/aliyun/deepseek/anthropic/glm',
    `base_url` VARCHAR(200) NOT NULL COMMENT 'API请求地址',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `api_key` VARCHAR(500) NOT NULL COMMENT 'API Key(加密存储)',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为默认模型',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_enabled` (`enabled`),
    INDEX `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- ============================================
-- 2. 邮箱配置表
-- ============================================
CREATE TABLE IF NOT EXISTS `email_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱地址',
    `password` VARCHAR(255) NOT NULL COMMENT '邮箱授权码/密码',
    `host` VARCHAR(100) NOT NULL COMMENT '邮箱服务器主机',
    `protocol` VARCHAR(20) DEFAULT 'imap' COMMENT '协议类型: imap, pop3',
    `port` INT DEFAULT 993 COMMENT '端口号',
    `ssl_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用SSL',
    `enabled` TINYINT(1) DEFAULT 0 COMMENT '是否启用监听',
    `folder` VARCHAR(50) DEFAULT 'INBOX' COMMENT '监听文件夹',
    `poll_interval` INT DEFAULT 30 COMMENT '轮询间隔(秒)',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱配置表';

-- ============================================
-- 3. 文件上传记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `user_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录名',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `display_name` VARCHAR(100) DEFAULT NULL COMMENT '显示名',
    `role` VARCHAR(30) DEFAULT 'USER' COMMENT '角色',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `email_verified` TINYINT(1) DEFAULT 0 COMMENT '邮箱是否已验证',
    `token_version` INT NOT NULL DEFAULT 0 COMMENT 'JWT版本号，用于令牌失效',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_account_username` (`username`),
    UNIQUE KEY `uk_user_account_email` (`email`),
    INDEX `idx_user_account_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS `oauth_account` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
    `provider` VARCHAR(30) NOT NULL COMMENT '提供商: github',
    `provider_user_id` VARCHAR(100) NOT NULL COMMENT '第三方用户ID',
    `login` VARCHAR(100) DEFAULT NULL COMMENT '第三方登录名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_oauth_provider_user` (`provider`, `provider_user_id`),
    INDEX `idx_oauth_user_id` (`user_id`),
    CONSTRAINT `fk_oauth_account_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE
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
-- 3. 文件上传记录表
-- ============================================
CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_path` VARCHAR(500) COMMENT '文件存储路径',
    `file_type` VARCHAR(50) COMMENT '文件类型',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `content` TEXT COMMENT '文件内容(文本提取)',
    `importance` INT COMMENT '重要程度(1-5): 1不重要, 5非常重要',
    `tags` VARCHAR(500) COMMENT '标签(逗号分隔)',
    `sentiment` VARCHAR(20) COMMENT '情感倾向: POSITIVE, NEGATIVE, NEUTRAL',
    `summary` VARCHAR(1000) COMMENT 'AI生成的摘要',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '处理状态: pending, analyzed, failed',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_importance` (`importance`),
    INDEX `idx_status` (`status`),
    INDEX `idx_file_type` (`file_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录表';

-- ============================================
-- 4. 日程事件表
-- ============================================
CREATE TABLE IF NOT EXISTS `schedule_event` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `title` VARCHAR(255) NOT NULL COMMENT '事件标题',
    `description` TEXT COMMENT '事件描述',
    `event_time` DATETIME COMMENT '事件时间',
    `event_date` DATE COMMENT '事件日期（用于快速查询）',
    `location` VARCHAR(255) COMMENT '地点',
    `source_email_id` BIGINT COMMENT '来源邮件ID',
    `source_email` VARCHAR(255) COMMENT '来源邮件地址',
    `file_path` VARCHAR(500) COMMENT '日程文件存储路径',
    `reminder_status` VARCHAR(20) DEFAULT 'pending' COMMENT '提醒状态: pending, sent, failed',
    `summary_status` VARCHAR(20) DEFAULT 'pending' COMMENT '汇总状态: pending, sent, failed',
    `reminder_enabled` BOOLEAN DEFAULT TRUE COMMENT '是否启用提醒',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending, completed, cancelled',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_event_date` (`event_date`),
    INDEX `idx_reminder_status` (`reminder_status`),
    INDEX `idx_summary_status` (`summary_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程事件表';

-- ============================================
-- 5. 定时任务表
-- ============================================
CREATE TABLE IF NOT EXISTS `scheduled_task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL COMMENT '任务名称',
    `description` VARCHAR(500) COMMENT '任务描述',
    `task_type` VARCHAR(50) NOT NULL COMMENT '任务类型: SKILL/CHAT/EMAIL/REMINDER',
    `cron_expression` VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    `params` TEXT COMMENT '任务参数(JSON格式)',
    `skill_code` VARCHAR(100) COMMENT '技能代码(任务类型为SKILL时使用)',
    `last_execute_time` DATETIME COMMENT '最后执行时间',
    `last_execute_result` TEXT COMMENT '最后执行结果',
    `next_execute_time` DATETIME COMMENT '下次执行时间',
    `execute_count` INT DEFAULT 0 COMMENT '执行次数',
    `success_count` INT DEFAULT 0 COMMENT '成功次数',
    `fail_count` INT DEFAULT 0 COMMENT '失败次数',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_enabled` (`enabled`),
    INDEX `idx_task_type` (`task_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

-- ============================================
-- 6. 聊天会话表
-- ============================================
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    `title` VARCHAR(255) DEFAULT '新会话' COMMENT '会话标题',
    `summary` VARCHAR(500) COMMENT '会话摘要',
    `message_count` INT DEFAULT 0 COMMENT '消息数量',
    `last_message_time` DATETIME COMMENT '最后消息时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: user/assistant',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `model` VARCHAR(50) COMMENT '使用的模型',
    `token_count` INT COMMENT 'token数量',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_session_time` (`session_id`, `create_time`),
    FOREIGN KEY (`session_id`) REFERENCES `chat_session`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ============================================
-- 7. 知识库表
-- ============================================
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(500) COMMENT '知识库描述',
    `collection_name` VARCHAR(100) NOT NULL COMMENT 'Qdrant集合名称',
    `embedding_model` VARCHAR(100) DEFAULT 'nomic-embed-text' COMMENT '向量模型名称',
    `chunk_size` INT DEFAULT 500 COMMENT '文档分块大小',
    `chunk_overlap` INT DEFAULT 50 COMMENT '分块重叠大小',
    `document_count` INT DEFAULT 0 COMMENT '文档数量',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_collection_name` (`collection_name`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库配置表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `base_id` BIGINT NOT NULL COMMENT '知识库ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(500) COMMENT '文件存储路径',
    `file_type` VARCHAR(20) COMMENT '文件类型',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `content` LONGTEXT COMMENT '文档内容',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/processing/completed/failed',
    `error_message` VARCHAR(500) COMMENT '错误信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_base_id` (`base_id`),
    INDEX `idx_status` (`status`),
    FOREIGN KEY (`base_id`) REFERENCES `knowledge_base`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ============================================
-- 8. 笔记表 & 代码片段表
-- ============================================
CREATE TABLE IF NOT EXISTS `note` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
    `title` VARCHAR(255) NOT NULL COMMENT '标题',
    `file_path` VARCHAR(500) COMMENT '文件存储路径',
    `tags` VARCHAR(255) COMMENT '标签(逗号分隔)',
    `ai_summary` VARCHAR(500) COMMENT 'AI生成的摘要',
    `is_pinned` BOOLEAN DEFAULT FALSE COMMENT '是否置顶',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- 代码片段表
CREATE TABLE IF NOT EXISTS `code_snippet` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '片段ID',
    `title` VARCHAR(255) NOT NULL COMMENT '标题',
    `code` TEXT NOT NULL COMMENT '代码内容',
    `language` VARCHAR(50) COMMENT '编程语言',
    `description` VARCHAR(500) COMMENT '描述',
    `tags` VARCHAR(255) COMMENT '标签(逗号分隔)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码片段表';

-- 创建索引
CREATE INDEX `idx_note_pinned` ON `note`(`is_pinned` DESC, `update_time` DESC);
CREATE INDEX `idx_snippet_language` ON `code_snippet`(`language`);

-- ============================================
-- 9. MCP 工具配置表
-- ============================================
CREATE TABLE IF NOT EXISTS `mcp_tool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '工具名称，唯一标识',
    `display_name` VARCHAR(100) COMMENT '工具显示名称',
    `description` VARCHAR(1000) COMMENT '工具描述，AI会根据此描述决定是否调用',
    `tool_type` VARCHAR(20) NOT NULL COMMENT '工具类型: http_api(外部HTTP接口), local_script(本地脚本)',
    `config` JSON NOT NULL COMMENT '工具配置，JSON格式',
    `input_schema` JSON COMMENT '输入参数Schema，JSON Schema格式',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具配置表';

-- ============================================
-- 10. 技能表
-- ============================================
CREATE TABLE IF NOT EXISTS `skill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code` VARCHAR(50) NOT NULL COMMENT '技能编码，系统唯一标识',
    `name` VARCHAR(100) NOT NULL COMMENT '技能名称',
    `description` VARCHAR(1000) COMMENT '技能描述，AI调用依据',
    `category` VARCHAR(50) COMMENT '技能分类：search, data, system, ai, custom',
    `icon` VARCHAR(50) COMMENT '图标名称',
    `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `is_builtin` TINYINT(1) DEFAULT 0 COMMENT '是否内置技能: 0-用户自定义, 1-系统内置',
    `config` JSON COMMENT '技能配置，JSON格式',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_category` (`category`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI技能表';

-- 技能-工具映射表（一个技能可调用多个工具，支持链式调用）
CREATE TABLE IF NOT EXISTS `skill_tool_mapping` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skill_id` BIGINT NOT NULL COMMENT '技能ID',
    `tool_id` BIGINT NOT NULL COMMENT 'MCP工具ID',
    `invoke_order` INT DEFAULT 0 COMMENT '调用顺序，数字越小越先执行',
    `is_required` TINYINT(1) DEFAULT 1 COMMENT '是否必须: 0-可选, 1-必须',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_tool` (`skill_id`, `tool_id`),
    KEY `idx_skill_id` (`skill_id`),
    KEY `idx_tool_id` (`tool_id`),
    CONSTRAINT `fk_skill_tool_skill` FOREIGN KEY (`skill_id`) REFERENCES `skill`(`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_skill_tool_tool` FOREIGN KEY (`tool_id`) REFERENCES `mcp_tool`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能-工具映射表';

-- ============================================
-- 示例数据
-- ============================================

-- MCP工具示例数据
INSERT INTO `mcp_tool` (`name`, `display_name`, `description`, `tool_type`, `config`, `input_schema`, `enabled`, `remark`) VALUES
('weather_query', '天气查询', '查询指定城市的天气信息，返回温度、湿度、天气状况等', 'http_api',
 '{"url": "https://api.example.com/weather", "method": "GET", "headers": {}, "timeout": 30}',
 '{"type": "object", "properties": {"city": {"type": "string", "description": "城市名称"}}, "required": ["city"]}',
 0, '示例：天气查询工具'),
('web_search', '网络搜索', '在互联网上搜索相关信息', 'http_api',
 '{"url": "https://api.serper.dev/search", "method": "POST", "headers": {"X-API-KEY": "${SERPER_API_KEY}"}, "timeout": 30}',
 '{"type": "object", "properties": {"query": {"type": "string", "description": "搜索关键词"}}, "required": ["query"]}',
 0, '示例：网络搜索工具'),
('file_read', '文件读取', '读取本地文件内容', 'local_script',
 '{"scriptPath": "./scripts/file_read.sh", "timeout": 10, "workingDir": "."}',
 '{"type": "object", "properties": {"filepath": {"type": "string", "description": "文件路径"}}, "required": ["filepath"]}',
 0, '示例：本地文件读取工具'),
('system_info', '系统信息', '获取系统运行状态信息', 'local_script',
 '{"scriptPath": "./scripts/system_info.sh", "timeout": 5}',
 '{"type": "object", "properties": {}, "required": []}',
 0, '示例：系统信息查询工具');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 技能分类说明：
-- search: 搜索类技能（网络搜索、文档搜索等）
-- data: 数据类技能（天气查询、股票查询、数据库查询等）
-- system: 系统类技能（文件操作、邮件发送、任务调度等）
-- ai: AI类技能（对话、内容分析、翻译等）
-- custom: 用户自定义技能
-- ============================================
