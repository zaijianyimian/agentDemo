-- 邮箱配置表
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

-- 示例数据 (请替换为实际的邮箱和授权码)
-- INSERT INTO `email_config` (`email`, `password`, `host`, `protocol`, `port`, `ssl_enabled`, `enabled`, `remark`)
-- VALUES ('your_email@qq.com', 'your_auth_code', 'imap.qq.com', 'imap', 993, 1, 0, 'QQ邮箱测试');