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
 * <p>MySQL 仍然是 Java 唯一业务数据源。对已完成多用户改造的表，通过 TenantLine 自动追加
 * {@code user_id} 条件；后台线程没有登录态时不启用过滤，保证邮件监听和定时任务可以跨用户运行。</p>
 */
@Configuration
public class MybatisPlusConfig implements MetaObjectHandler {

    private static final Set<String> USER_SCOPED_TABLES = Set.of(
            "email_config",
            "chat_session"
    );

    private final CurrentUserProvider currentUserProvider;

    public MybatisPlusConfig(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 注册多用户隔离与分页插件。
     *
     * @return MyBatis Plus 拦截器。
     */
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

    /**
     * 插入时自动填充时间字段。
     *
     * @param metaObject MyBatis 元对象。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充更新时间。
     *
     * @param metaObject MyBatis 元对象。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
