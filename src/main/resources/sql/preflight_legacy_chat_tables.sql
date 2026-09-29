-- 只读预检查。先审阅结果，再在维护窗口单独执行 drop_legacy_chat_tables.sql。
-- 行数用于确认待舍弃数据量。
SELECT 'chat_session' AS table_name, COUNT(*) AS row_count FROM chat_session
UNION ALL SELECT 'chat_message', COUNT(*) FROM chat_message
UNION ALL SELECT 'chat_history', COUNT(*) FROM chat_history;

-- 结果应为空：其他表引用待删除表的外键。
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME NOT IN ('chat_session', 'chat_message', 'chat_history')
  AND REFERENCED_TABLE_NAME IN ('chat_session', 'chat_message', 'chat_history');

-- 结果应为空：待删除表上的触发器。
SELECT TABLE_NAME, TRIGGER_NAME
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE()
  AND EVENT_OBJECT_TABLE IN ('chat_session', 'chat_message', 'chat_history');

-- 结果应为空：引用待删除表的视图。
SELECT TABLE_NAME
FROM information_schema.VIEWS
WHERE TABLE_SCHEMA = DATABASE()
  AND (VIEW_DEFINITION LIKE '%chat_session%'
       OR VIEW_DEFINITION LIKE '%chat_message%'
       OR VIEW_DEFINITION LIKE '%chat_history%');
