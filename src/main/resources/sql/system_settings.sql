-- 系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50) NOT NULL COMMENT '配置分类: system/database/search/mail/schedule/file',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(255) COMMENT '配置描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_key (category, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';

-- 初始化一些默认配置
INSERT INTO system_settings (category, config_key, config_value, description) VALUES
-- 系统设置
('system', 'site_name', 'AI Agent', '系统名称'),
('system', 'site_logo', '', '系统Logo URL'),
('system', 'default_theme', 'light', '默认主题'),

-- 模型参数设置
('model', 'temperature', '0.7', '模型温度'),
('model', 'maxTokens', '4096', '最大Token数'),
('model', 'topP', '0.9', 'Top P 核采样'),
('model', 'memorySize', '20', '上下文记忆数量'),
('model', 'systemPrompt', '你是一个有帮助的AI助手，请用简洁、准确的语言回答问题。', '系统提示词'),

-- 向量数据库配置
('qdrant', 'host', 'localhost', 'Qdrant 服务地址'),
('qdrant', 'port', '6334', 'Qdrant 服务端口'),
('qdrant', 'collection_name', 'agent_memory', '向量集合名称'),
('qdrant', 'top_k', '5', '返回结果数量'),
('qdrant', 'min_score', '0.5', '最小相似度分数'),

-- 搜索配置
('search', 'enabled', 'true', '是否启用搜索'),
('search', 'engine', 'serper', '搜索引擎: serper/tavily/bing'),
('search', 'api_key', '', '搜索API密钥'),
('search', 'max_results', '3', '最大搜索结果数'),

-- 日程配置
('schedule', 'enabled', 'true', '是否启用日程功能'),
('schedule', 'storage_path', './data/schedules', '日程文件存储路径'),
('schedule', 'user_email', '', '用户接收邮件地址'),
('schedule', 'daily_summary_cron', '0 0 20 * * ?', '每日汇总发送时间'),
('schedule', 'morning_reminder_cron', '0 0 8 * * ?', '早上提醒时间'),

-- 文件上传配置
('file', 'upload_dir', './data/documents', '文件上传目录'),
('file', 'allowed_types', 'txt,md,pdf,doc,docx', '允许的文件类型'),
('file', 'max_file_size', '10MB', '最大文件大小')
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;