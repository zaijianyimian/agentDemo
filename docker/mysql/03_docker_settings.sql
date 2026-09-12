-- Java 的 MySQL 只保存业务与执行基础设施配置。
-- Agent、模型、Memory、RAG、Qdrant 等运行时配置由 Python/PostgreSQL 管理。
DELETE FROM `system_settings`
WHERE `category` IN ('model', 'qdrant', 'memory', 'embedding', 'mcp', 'skill', 'autonomy');
