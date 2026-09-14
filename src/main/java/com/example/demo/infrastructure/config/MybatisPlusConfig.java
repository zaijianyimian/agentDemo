package com.example.demo.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.demo.shared.context.CurrentUserContext;
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
 * {@code user_id} 条件；后台处理在访问用户数据前打开可信的 {@code ExecutionContextScope}，
 * 保证线程复用时仍遵守同一租户边界。</p>
 */
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    /**
     * 强租户隔离表。mcp_tool / skill 采用“系统内置 + 用户私有”的混合范围，不在此集合；
     * system_settings 属于平台运行配置，也保持全局。
     */
    private static final Set<String> USER_SCOPED_TABLES = Set.of(
            "email_config",
            "email_listener_state",
            "chat_session",
            "chat_message",
            "schedule_event",
            "scheduled_task",
            "job_log",
            "code_snippet",
            "document",
            "chat_history",
            "virtual_assistant",
            "knowledge_base",
            "knowledge_document",
            "search_history",
            "user_interest",
            "dispatched_task",
            "dispatch_result_outbox",
            "consumed_event",
            "push_config"
    );

    private final CurrentUserContext currentUserProvider;

    public MybatisPlusConfig(CurrentUserContext currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /** 注册多用户隔离与分页插件。 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new UserTenantLineHandler(currentUserProvider)));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    static final class UserTenantLineHandler implements TenantLineHandler {
        private final CurrentUserContext currentUser;

        UserTenantLineHandler(CurrentUserContext currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public Expression getTenantId() {
            return new LongValue(currentUser.requireUserId());
        }

        @Override
        public String getTenantIdColumn() {
            return "user_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return !USER_SCOPED_TABLES.contains(tableName.toLowerCase());
        }
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
