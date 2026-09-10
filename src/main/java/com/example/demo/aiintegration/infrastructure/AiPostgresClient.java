package com.example.demo.aiintegration.infrastructure;

import com.example.demo.aiintegration.config.AiIntegrationProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 专用 PostgreSQL 访问入口。
 *
 * <p>该类内部持有独立 Hikari 连接池，但不把 {@link javax.sql.DataSource} 注册为 Spring Bean，
 * 避免干扰现有 MySQL + MyBatis 主数据源自动配置，也不需要动态数据源切换。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.graph", name = "enabled", havingValue = "true")
public class AiPostgresClient {

    private final HikariDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建 AI PostgreSQL 独立连接池。
     *
     * @param properties Graph 集成配置
     */
    public AiPostgresClient(AiIntegrationProperties properties) {
        AiIntegrationProperties.Postgres postgres = properties.getPostgres();
        HikariConfig config = new HikariConfig();
        config.setPoolName("agent-ai-postgres");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(Math.max(1, postgres.getMaximumPoolSize()));
        config.setMinimumIdle(Math.max(0, Math.min(
                postgres.getMinimumIdle(),
                postgres.getMaximumPoolSize()
        )));
        config.setConnectionTimeout(Math.max(250L, postgres.getConnectionTimeoutMs()));
        config.setAutoCommit(true);
        this.dataSource = new HikariDataSource(config);
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 获取仅供 AI 集成模块使用的 JDBC 模板。
     *
     * @return AI PostgreSQL JDBC 模板
     */
    public NamedParameterJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    /**
     * 关闭 AI PostgreSQL 独立连接池。
     */
    @PreDestroy
    public void close() {
        dataSource.close();
    }
}
