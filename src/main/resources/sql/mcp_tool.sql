-- MCP 工具配置表
-- 存储可被 AI 调用的工具描述信息

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

-- 示例数据：HTTP API 类型工具
INSERT INTO `mcp_tool` (`name`, `display_name`, `description`, `tool_type`, `config`, `input_schema`, `enabled`, `remark`) VALUES
('weather_query', '天气查询', '查询指定城市的天气信息，返回温度、湿度、天气状况等', 'http_api',
 '{"url": "https://api.example.com/weather", "method": "GET", "headers": {}, "timeout": 30}',
 '{"type": "object", "properties": {"city": {"type": "string", "description": "城市名称"}}, "required": ["city"]}',
 0, '示例：天气查询工具'),

('web_search', '网络搜索', '在互联网上搜索相关信息', 'http_api',
 '{"url": "https://api.serper.dev/search", "method": "POST", "headers": {"X-API-KEY": "${SERPER_API_KEY}"}, "timeout": 30}',
 '{"type": "object", "properties": {"query": {"type": "string", "description": "搜索关键词"}}, "required": ["query"]}',
 0, '示例：网络搜索工具');

-- 示例数据：本地脚本类型工具
INSERT INTO `mcp_tool` (`name`, `display_name`, `description`, `tool_type`, `config`, `input_schema`, `enabled`, `remark`) VALUES
('file_read', '文件读取', '读取本地文件内容', 'local_script',
 '{"scriptPath": "./scripts/file_read.sh", "timeout": 10, "workingDir": "."}',
 '{"type": "object", "properties": {"filepath": {"type": "string", "description": "文件路径"}}, "required": ["filepath"]}',
 0, '示例：本地文件读取工具'),

('system_info', '系统信息', '获取系统运行状态信息', 'local_script',
 '{"scriptPath": "./scripts/system_info.sh", "timeout": 5}',
 '{"type": "object", "properties": {}, "required": []}',
 0, '示例：系统信息查询工具');