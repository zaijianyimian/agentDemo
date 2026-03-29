-- 搜索历史表
CREATE TABLE IF NOT EXISTS search_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query VARCHAR(500) NOT NULL COMMENT '搜索关键词',
    search_mode VARCHAR(20) DEFAULT 'normal' COMMENT '搜索模式: normal/summary/stream',
    result_count INT DEFAULT 0 COMMENT '搜索结果数量',
    has_summary BOOLEAN DEFAULT FALSE COMMENT '是否有AI总结',
    duration_ms BIGINT COMMENT '搜索耗时（毫秒）',
    session_id VARCHAR(100) COMMENT '用户会话ID',
    source_ip VARCHAR(50) COMMENT '搜索来源IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

-- 用户兴趣表
CREATE TABLE IF NOT EXISTS user_interest (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag VARCHAR(100) NOT NULL COMMENT '兴趣标签名称',
    category VARCHAR(50) DEFAULT 'other' COMMENT '兴趣分类: technology/business/entertainment/sports/health/education/other',
    weight INT DEFAULT 1 COMMENT '兴趣权重',
    related_keywords TEXT COMMENT '相关搜索关键词（JSON数组）',
    last_search_time DATETIME COMMENT '最后搜索时间',
    search_count INT DEFAULT 1 COMMENT '搜索次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣表';

-- 创建索引加速查询
CREATE INDEX idx_search_history_create_time ON search_history(create_time);
CREATE INDEX idx_search_history_query ON search_history(query);