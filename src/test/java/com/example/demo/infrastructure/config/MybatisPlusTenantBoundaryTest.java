package com.example.demo.infrastructure.config;

import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisPlusTenantBoundaryTest {

    @Test
    void userTablesNeverBecomeUnscopedWhenIdentityIsMissing() {
        var handler = new MybatisPlusConfig.UserTenantLineHandler(() -> Optional.empty());

        assertThat(handler.ignoreTable("scheduled_task")).isFalse();
        assertThat(handler.ignoreTable("system_settings")).isTrue();
        assertThatThrownBy(handler::getTenantId).isInstanceOf(AccessDeniedException.class);
        var interceptor = new TenantLineInnerInterceptor(handler);
        assertThatThrownBy(() -> interceptor.buildTableExpression(
                new Table("scheduled_task"), null, "SELECT * FROM scheduled_task"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void tenantExpressionUsesOnlyTrustedCurrentUser() {
        CurrentUserContext current = () -> Optional.of(new UserContext(42));
        var handler = new MybatisPlusConfig.UserTenantLineHandler(current);

        assertThat(handler.getTenantId().toString()).isEqualTo("42");
        var expression = new TenantLineInnerInterceptor(handler)
                .buildTableExpression(new Table("scheduled_task"), null, "SELECT * FROM scheduled_task");
        assertThat(expression.toString()).isEqualTo("user_id = 42");
    }
}
