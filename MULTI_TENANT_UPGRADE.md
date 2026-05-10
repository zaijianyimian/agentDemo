# 单体版升级多租户版指南

> 文档版本：1.0
> 更新时间：2026-04-06
> 适用项目：AI Agent Demo

本文档详细说明如何将当前单用户版本升级为多租户版本，涵盖数据库改造、后端架构调整、前端改造和安全策略等多个方面。

## 目录

1. [概述](#1-概述)
2. [数据库改造](#2-数据库改造)
3. [后端架构改造](#3-后端架构改造)
4. [认证授权改造](#4-认证授权改造)
5. [前端改造](#5-前端改造)
6. [数据迁移](#6-数据迁移)
7. [部署与运维](#7-部署与运维)
8. [测试策略](#8-测试策略)

---

## 1. 概述

### 1.1 当前架构

当前项目为**单用户版本**，主要特点：

- 单一用户账号体系
- 所有数据归属于单一用户
- 无租户隔离机制
- 配置全局共享

### 1.2 目标架构

升级为**多租户版本**，支持：

- 多租户隔离（Schema 隔离或 Row 隔离）
- 租户独立配置
- 租户配额管理
- 租户计费支持

### 1.3 隔离策略选择

| 策略 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **Schema 隔离** | 数据完全隔离、安全性高、备份恢复方便 | 连接池管理复杂、迁移成本高 | 企业级 SaaS、数据安全要求高 |
| **Row 隔离** | 实现简单、资源利用率高 | 数据隔离性弱、查询性能受影响 | 中小型 SaaS、成本敏感场景 |
| **Database 隔离** | 完全隔离、可独立扩展 | 运维成本最高 | 大型企业独立部署 |

**推荐方案**：Schema 隔离（平衡安全性与运维成本）

---

## 2. 数据库改造

### 2.1 新增租户管理表

在公共 Schema 中创建租户管理表：

```sql
-- 租户表
CREATE TABLE IF NOT EXISTS `tenant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '租户ID',
    `tenant_code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一标识）',
    `name` VARCHAR(100) NOT NULL COMMENT '租户名称',
    `contact_email` VARCHAR(100) COMMENT '联系人邮箱',
    `contact_phone` VARCHAR(20) COMMENT '联系人电话',
    `plan` VARCHAR(30) NOT NULL DEFAULT 'free' COMMENT '套餐: free/basic/pro/enterprise',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/suspended/cancelled',
    `max_users` INT NOT NULL DEFAULT 1 COMMENT '最大用户数',
    `max_storage_mb` INT NOT NULL DEFAULT 100 COMMENT '最大存储空间(MB)',
    `max_api_calls` INT NOT NULL DEFAULT 1000 COMMENT '每日API调用限制',
    `current_storage_mb` INT DEFAULT 0 COMMENT '当前存储使用量(MB)',
    `schema_name` VARCHAR(50) COMMENT '对应的数据库Schema名称',
    `expire_time` DATETIME COMMENT '套餐过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_tenant_code` (`tenant_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 租户配额使用记录
CREATE TABLE IF NOT EXISTS `tenant_quota_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `quota_type` VARCHAR(30) NOT NULL COMMENT '配额类型: storage/api_calls/users',
    `usage_value` INT NOT NULL COMMENT '使用量',
    `log_date` DATE NOT NULL COMMENT '记录日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tenant_date` (`tenant_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户配额日志';

-- 租户订阅记录
CREATE TABLE IF NOT EXISTS `tenant_subscription` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `plan` VARCHAR(30) NOT NULL COMMENT '套餐',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `amount` DECIMAL(10,2) COMMENT '金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户订阅记录';
```

### 2.2 用户表改造

修改 `user_account` 表，增加租户关联：

```sql
-- 在公共 Schema 中
ALTER TABLE `user_account`
    ADD COLUMN `tenant_id` BIGINT COMMENT '租户ID',
    ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '角色: owner/admin/member',
    ADD INDEX `idx_tenant_id` (`tenant_id`);

-- 修改唯一约束，允许不同租户有相同用户名
ALTER TABLE `user_account`
    DROP INDEX `uk_username`,
    ADD UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`),
    DROP INDEX `uk_email`,
    ADD UNIQUE KEY `uk_tenant_email` (`tenant_id`, `email`);
```

### 2.3 业务表改造（Schema 隔离方案）

为每个租户创建独立 Schema，业务表结构保持不变：

```sql
-- 创建租户 Schema 模板
CREATE SCHEMA IF NOT EXISTS `tenant_{tenant_code}`;

-- 在租户 Schema 中创建业务表
-- 这些表结构与当前单用户版本一致，但不需要 user_id 字段
-- 因为整个 Schema 属于同一租户
```

### 2.4 业务表改造（Row 隔离方案）

如果选择 Row 隔离，所有业务表需添加 `tenant_id`：

```sql
-- 为所有业务表添加 tenant_id
ALTER TABLE `chat_session` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `chat_message` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `document` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `note` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `schedule_event` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `email_config` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `mcp_tool` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `skill` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `knowledge_base` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `scheduled_task` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `ai_model_config` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `system_settings` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `chat_history` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
ALTER TABLE `virtual_assistant` ADD COLUMN `tenant_id` BIGINT NOT NULL, ADD INDEX `idx_tenant_id` (`tenant_id`);
```

---

## 3. 后端架构改造

### 3.1 租户上下文管理

创建租户上下文持有类：

```java
package com.example.demo.tenant;

/**
 * 租户上下文持有者
 * 使用 ThreadLocal 存储当前请求的租户信息
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setTenantCode(String tenantCode) {
        TENANT_CODE.set(tenantCode);
    }

    public static String getTenantCode() {
        return TENANT_CODE.get();
    }

    public static void clear() {
        TENANT_ID.remove();
        TENANT_CODE.remove();
    }
}
```

### 3.2 租户拦截器

```java
package com.example.demo.tenant;

import com.example.demo.entity.UserAccount;
import com.example.demo.service.auth.AuthUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器
 * 从认证信息中提取租户ID并设置到上下文
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final AuthUserService authUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            UserAccount user = authUserService.getByUsername(username);
            if (user != null && user.getTenantId() != null) {
                TenantContext.setTenantId(user.getTenantId());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                  Object handler, Exception ex) {
        TenantContext.clear();
    }
}
```

### 3.3 数据源路由（Schema 隔离）

```java
package com.example.demo.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多租户数据源路由
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Long, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final DataSource defaultDataSource;

    public TenantRoutingDataSource(DataSource defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
        setDefaultTargetDataSource(defaultDataSource);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return defaultDataSource;
        }
        return tenantDataSources.computeIfAbsent(tenantId, this::createTenantDataSource);
    }

    private DataSource createTenantDataSource(Long tenantId) {
        // 根据 tenantId 创建对应 Schema 的数据源
        // 实际实现需要从 tenant 表获取 schema_name
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/tenant_" + tenantId);
        ds.setUsername("root");
        ds.setPassword("password");
        return ds;
    }
}
```

### 3.4 MyBatis-Plus 租户插件（Row 隔离）

```java
package com.example.demo.tenant;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 多租户配置
 */
@Configuration
public class TenantMybatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                return tenantId != null ? new LongValue(tenantId) : new LongValue(0);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 公共表不需要租户过滤
                return tableName.equals("tenant") ||
                       tableName.equals("tenant_quota_log") ||
                       tableName.equals("tenant_subscription") ||
                       tableName.equals("user_account");
            }
        });

        interceptor.addInnerInterceptor(tenantInterceptor);
        return interceptor;
    }
}
```

### 3.5 租户服务

```java
package com.example.demo.tenant;

import com.example.demo.entity.Tenant;
import com.example.demo.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 租户管理服务
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    /**
     * 创建新租户
     */
    @Transactional
    public Tenant createTenant(String tenantCode, String name, String email, String plan) {
        Tenant tenant = Tenant.builder()
                .tenantCode(tenantCode)
                .name(name)
                .contactEmail(email)
                .plan(plan)
                .status("active")
                .maxUsers(getPlanLimit(plan, "users"))
                .maxStorageMb(getPlanLimit(plan, "storage"))
                .maxApiCalls(getPlanLimit(plan, "api_calls"))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        tenantMapper.insert(tenant);

        // 如果是 Schema 隔离，创建租户 Schema
        createTenantSchema(tenantCode);

        return tenant;
    }

    /**
     * 检查配额
     */
    public boolean checkQuota(Long tenantId, String quotaType) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) return false;

        return switch (quotaType) {
            case "storage" -> tenant.getCurrentStorageMb() < tenant.getMaxStorageMb();
            case "api_calls" -> true; // 需要查询日志表
            case "users" -> countTenantUsers(tenantId) < tenant.getMaxUsers();
            default -> false;
        };
    }

    private int getPlanLimit(String plan, String type) {
        return switch (plan) {
            case "free" -> switch (type) {
                case "users" -> 1;
                case "storage" -> 100;
                case "api_calls" -> 1000;
                default -> 0;
            };
            case "basic" -> switch (type) {
                case "users" -> 5;
                case "storage" -> 1024;
                case "api_calls" -> 10000;
                default -> 0;
            };
            case "pro" -> switch (type) {
                case "users" -> 20;
                case "storage" -> 10240;
                case "api_calls" -> 100000;
                default -> 0;
            };
            case "enterprise" -> switch (type) {
                case "users" -> -1; // 无限制
                case "storage" -> -1;
                case "api_calls" -> -1;
                default -> 0;
            };
            default -> 0;
        };
    }

    private void createTenantSchema(String tenantCode) {
        // 执行 Schema 创建 SQL
    }

    private int countTenantUsers(Long tenantId) {
        // 查询租户用户数
        return 0;
    }
}
```

---

## 4. 认证授权改造

### 4.1 JWT 改造

在 JWT 中添加租户信息：

```java
package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（多租户版本）
 */
@Component
public class JwtTokenProvider {

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @Value("${app.security.jwt-expiration}")
    private long jwtExpiration;

    public String generateToken(String username, Long tenantId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }

    public Long getTenantIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        return claims.get("tenant_id", Long.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }
}
```

### 4.2 权限控制

```java
package com.example.demo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 租户权限检查
 */
@Component("tenantAuth")
@RequiredArgsConstructor
public class TenantAuthorization {

    /**
     * 检查当前用户是否为租户管理员
     */
    public boolean isTenantAdmin() {
        String role = SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
        return "ROLE_TENANT_ADMIN".equals(role) || "ROLE_TENANT_OWNER".equals(role);
    }

    /**
     * 检查资源是否属于当前租户
     */
    public boolean belongsToCurrentTenant(Long resourceTenantId) {
        Long currentTenantId = TenantContext.getTenantId();
        return currentTenantId != null && currentTenantId.equals(resourceTenantId);
    }
}
```

在控制器中使用：

```java
@PreAuthorize("@tenantAuth.isTenantAdmin()")
@DeleteMapping("/tenant/settings")
public ApiResponse<Void> updateTenantSettings(...) {
    // 只有租户管理员可以操作
}
```

---

## 5. 前端改造

### 5.1 登录流程改造

```typescript
// stores/auth.ts
interface AuthState {
  user: User | null
  tenant: Tenant | null
  token: string | null
}

// 登录时同时获取租户信息
async function login(username: string, password: string) {
  const response = await axios.post('/api/auth/login', { username, password })
  const { accessToken, user, tenant } = response.data.data

  this.user = user
  this.tenant = tenant
  this.token = accessToken

  localStorage.setItem('tenant_id', tenant.id)
}
```

### 5.2 租户切换（如果支持）

```vue
<!-- components/TenantSelector.vue -->
<template>
  <n-select
    v-model:value="currentTenantId"
    :options="tenantOptions"
    @update:value="switchTenant"
  />
</template>

<script setup lang="ts">
const currentTenantId = ref(localStorage.getItem('tenant_id'))

async function switchTenant(tenantId: string) {
  await axios.post('/api/tenant/switch', { tenantId })
  localStorage.setItem('tenant_id', tenantId)
  window.location.reload()
}
</script>
```

### 5.3 租户配额展示

```vue
<!-- components/TenantQuota.vue -->
<template>
  <n-card title="资源使用">
    <n-progress
      type="line"
      :percentage="storagePercent"
      :status="storageStatus"
    >
      存储: {{ tenant.currentStorageMb }} / {{ tenant.maxStorageMb }} MB
    </n-progress>

    <n-progress
      type="line"
      :percentage="userPercent"
    >
      用户: {{ currentUserCount }} / {{ tenant.maxUsers }}
    </n-progress>
  </n-card>
</template>
```

---

## 6. 数据迁移

### 6.1 迁移脚本

```sql
-- 1. 创建默认租户
INSERT INTO tenant (tenant_code, name, plan, status, max_users, max_storage_mb, max_api_calls)
VALUES ('default', 'Default Tenant', 'enterprise', 'active', -1, -1, -1);

-- 2. 将现有用户关联到默认租户
UPDATE user_account SET tenant_id = 1 WHERE tenant_id IS NULL;

-- 3. 为业务数据添加租户ID（Row 隔离方案）
UPDATE chat_session SET tenant_id = 1;
UPDATE chat_message SET tenant_id = 1;
UPDATE document SET tenant_id = 1;
UPDATE note SET tenant_id = 1;
UPDATE schedule_event SET tenant_id = 1;
UPDATE email_config SET tenant_id = 1;
UPDATE mcp_tool SET tenant_id = 1;
UPDATE skill SET tenant_id = 1;
UPDATE knowledge_base SET tenant_id = 1;
UPDATE scheduled_task SET tenant_id = 1;
UPDATE ai_model_config SET tenant_id = 1;
UPDATE system_settings SET tenant_id = 1;
UPDATE chat_history SET tenant_id = 1;
UPDATE virtual_assistant SET tenant_id = 1;

-- 4. 设置第一个用户为租户所有者
UPDATE user_account SET role = 'owner' WHERE id = 1;
```

### 6.2 迁移工具

```java
package com.example.demo.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据迁移服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void migrateToTenant(Long sourceTenantId, Long targetTenantId) {
        log.info("开始迁移数据: {} -> {}", sourceTenantId, targetTenantId);

        String[] tables = {
            "chat_session", "chat_message", "document", "note",
            "schedule_event", "email_config", "mcp_tool", "skill",
            "knowledge_base", "scheduled_task", "ai_model_config",
            "system_settings", "chat_history", "virtual_assistant"
        };

        for (String table : tables) {
            String sql = String.format(
                "UPDATE %s SET tenant_id = ? WHERE tenant_id = ?", table
            );
            int count = jdbcTemplate.update(sql, targetTenantId, sourceTenantId);
            log.info("表 {} 迁移了 {} 条记录", table, count);
        }

        log.info("数据迁移完成");
    }
}
```

---

## 7. 部署与运维

### 7.1 环境配置

```yaml
# application.yaml
app:
  tenant:
    enabled: true
    default-tenant-code: default
    schema-prefix: tenant_

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent
    username: root
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
```

### 7.2 监控指标

```java
package com.example.demo.tenant;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 租户监控指标
 */
@Component
public class TenantMetrics {

    private final Counter apiCallCounter;
    private final Gauge activeTenantsGauge;

    public TenantMetrics(MeterRegistry registry, TenantService tenantService) {
        this.apiCallCounter = Counter.builder("tenant_api_calls")
                .description("Tenant API calls")
                .tag("tenant", "default")
                .register(registry);

        this.activeTenantsGauge = Gauge.builder("active_tenants", tenantService::countActiveTenants)
                .description("Number of active tenants")
                .register(registry);
    }

    public void recordApiCall(Long tenantId) {
        apiCallCounter.increment();
    }
}
```

### 7.3 备份策略

```bash
#!/bin/bash
# backup_tenant.sh

TENANT_ID=$1
BACKUP_DIR="/backups/tenant_${TENANT_ID}_$(date +%Y%m%d)"

# Schema 隔离备份
mysqldump -u root -p tenant_${TENANT_ID} > ${BACKUP_DIR}/schema.sql

# Row 隔离备份
mysqldump -u root -p agent --where="tenant_id=${TENANT_ID}" > ${BACKUP_DIR}/data.sql

# 备份文件存储
aws s3 cp ${BACKUP_DIR} s3://backups/tenant_${TENANT_ID}/
```

---

## 8. 测试策略

### 8.1 单元测试

```java
@SpringBootTest
class TenantServiceTest {

    @Autowired
    private TenantService tenantService;

    @Test
    void testCreateTenant() {
        Tenant tenant = tenantService.createTenant(
            "test-tenant", "Test Tenant", "test@example.com", "basic"
        );

        assertNotNull(tenant.getId());
        assertEquals("basic", tenant.getPlan());
    }

    @Test
    void testTenantIsolation() {
        // 创建两个租户
        Tenant t1 = tenantService.createTenant("t1", "Tenant 1", "t1@test.com", "free");
        Tenant t2 = tenantService.createTenant("t2", "Tenant 2", "t2@test.com", "free");

        // 设置租户上下文
        TenantContext.setTenantId(t1.getId());

        // 验证只能看到 t1 的数据
        List<Document> docs = documentService.listAll();
        assertTrue(docs.stream().allMatch(d -> d.getTenantId().equals(t1.getId())));
    }
}
```

### 8.2 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void testMultiTenantApiAccess() {
        // 租户1 登录
        String token1 = login("user1", "password");

        // 租户2 登录
        String token2 = login("user2", "password");

        // 验证数据隔离
        given()
            .header("Authorization", "Bearer " + token1)
            .get("/api/documents")
            .then()
            .body("data.size()", equalTo(5)); // 租户1有5个文档

        given()
            .header("Authorization", "Bearer " + token2)
            .get("/api/documents")
            .then()
            .body("data.size()", equalTo(3)); // 租户2有3个文档
    }
}
```

---

## 附录

### A. 套餐定义

| 套餐 | 价格 | 用户数 | 存储 | API调用/日 |
|------|------|--------|------|------------|
| Free | 免费 | 1 | 100MB | 1,000 |
| Basic | ¥99/月 | 5 | 1GB | 10,000 |
| Pro | ¥299/月 | 20 | 10GB | 100,000 |
| Enterprise | 定制 | 无限 | 无限 | 无限 |

### B. 数据库变更清单

1. 新增表：`tenant`, `tenant_quota_log`, `tenant_subscription`
2. 修改表：`user_account` 添加 `tenant_id`, `role`
3. 业务表：根据隔离策略添加 `tenant_id` 或创建独立 Schema

### C. API 变更清单

| API | 变更 |
|-----|------|
| POST /api/auth/register | 添加 `tenant_code` 参数（可选，用于加入现有租户） |
| POST /api/auth/login | 返回值添加 `tenant` 信息 |
| GET /api/tenant/info | 新增：获取当前租户信息 |
| PUT /api/tenant/settings | 新增：更新租户设置（管理员） |
| GET /api/tenant/quota | 新增：获取租户配额使用情况 |
| POST /api/tenant/upgrade | 新增：升级套餐 |

### D. 回滚方案

```sql
-- 回滚多租户改造
ALTER TABLE user_account DROP COLUMN tenant_id;
ALTER TABLE user_account DROP COLUMN role;

-- 删除租户表
DROP TABLE IF EXISTS tenant;
DROP TABLE IF EXISTS tenant_quota_log;
DROP TABLE IF EXISTS tenant_subscription;

-- 删除业务表的 tenant_id
ALTER TABLE chat_session DROP COLUMN tenant_id;
-- ... 其他表同理
```

---

**文档维护**：本文档应随版本迭代持续更新。如有疑问请联系开发团队。