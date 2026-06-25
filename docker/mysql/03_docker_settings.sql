-- Docker Compose runtime overrides.
-- The application reads Qdrant connection settings from system_settings first.
UPDATE `system_settings`
SET `config_value` = 'qdrant'
WHERE `category` = 'qdrant'
  AND `config_key` = 'host';
