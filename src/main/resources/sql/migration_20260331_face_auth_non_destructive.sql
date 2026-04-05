-- ============================================
-- 非破坏性迁移脚本（人脸二次验证）
-- 生成时间: 2026-03-31
-- 说明:
-- 1) 不执行 DROP TABLE / DROP COLUMN
-- 2) 保留历史数据
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;
SET @db_name = DATABASE();

-- ============================================
-- 1) user_account 增加 face_auth_enabled（若不存在）
-- ============================================
SET @has_face_auth_enabled = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'user_account'
      AND column_name = 'face_auth_enabled'
);

SET @sql_face_auth_enabled = IF(
    @has_face_auth_enabled = 0,
    'ALTER TABLE `user_account`
       ADD COLUMN `face_auth_enabled` TINYINT(1) DEFAULT 0 COMMENT ''是否要求登录时进行人脸二次验证'' AFTER `email_verified`',
    'SELECT ''user_account.face_auth_enabled exists, skip add'' AS msg'
);
PREPARE stmt_face_auth_enabled FROM @sql_face_auth_enabled;
EXECUTE stmt_face_auth_enabled;
DEALLOCATE PREPARE stmt_face_auth_enabled;

UPDATE `user_account`
SET `face_auth_enabled` = 0
WHERE `face_auth_enabled` IS NULL;

-- ============================================
-- 2) user_face_profile（若不存在则创建）
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
    INDEX `idx_user_face_profile_enabled` (`enabled`),
    CONSTRAINT `fk_user_face_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户人脸认证向量表';
