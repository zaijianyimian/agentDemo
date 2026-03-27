-- AI Agent 技能系统数据库表
-- 技能是对 MCP 工具的高级封装，支持分类、元数据和链式调用

-- 技能表
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

-- 技能分类说明：
-- search: 搜索类技能（网络搜索、文档搜索等）
-- data: 数据类技能（天气查询、股票查询、数据库查询等）
-- system: 系统类技能（文件操作、邮件发送、任务调度等）
-- ai: AI类技能（对话、内容分析、翻译等）
-- custom: 用户自定义技能