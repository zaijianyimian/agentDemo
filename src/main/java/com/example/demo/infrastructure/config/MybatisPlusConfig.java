package com.example.demo.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * MyBatis Plus 配置类。
 *
 * <p>MySQL 仍然是 Java 唯一业务数据源。用户私有表通过 TenantLine 自动追加
 * {@code user_id} 条件；系统级后台扫描没有用户上下文时不启用过滤，扫描到具体任务后再由
 * UserExecutionContext 绑定 owner，保证后台执行阶段继续遵守租户边界。</p>
 */
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    /**
     * 强租户隔离表。mcp_tool / skill 采用“系统内置 + 用户私有”的混合范围，不在此集合；
     * push_config / system_settings 属于系统运行配置，也保持全局。
     */
    private static final Set<String> USER_SCOPED_TABLES = Set.of(
            "email_config",
            "chat_session",
            "schedule_event",
            "scheduled_task",
            "job_log",
            "note",
            "code_snippet",
            "document",
            "chat_history",
            "virtual_assistant",
            "knowledge_base",
            "knowledge_document",
            "search_history",
            "user_interest",
            "dispatched_task"
    );

    private final CurrentUserProvider currentUserProvider;

    public MybatisPlusConfig(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /** 注册多用户隔离与分页插件。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(currentUserProvider.requireUserId());
            }

            @Override
            public String getTenantIdColumn() {
                return "user_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return currentUserProvider.currentUserId().isEmpty()
                        || !USER_SCOPED_TABLES.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
