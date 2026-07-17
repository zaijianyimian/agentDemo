package com.example.demo.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.auth.domain.UserAccount;
import com.example.demo.auth.persistence.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * 用户账号读缓存。对 MyBatis-Plus 的查询方法使用 Spring Cache 注解包裹，
 * 通过不同键空间（id / username / email / login）减少数据库压力。
 */
public class UserAccountCacheService {

    private final UserAccountMapper userAccountMapper;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_ID, key = "#id", unless = "#result == null")
    /**
     * 按 id 查询用户，结果会写入名为 user-account-by-id 的缓存。
     */
    public UserAccount findById(Long id) {
        if (id == null) {
            return null;
        }
        return userAccountMapper.selectById(id);
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_USERNAME, key = "#normalizedUsername", unless = "#result == null")
    /**
     * 按用户名查询用户（调用方需自行 trim & 大小写处理），缓存键即 normalizedUsername。
     */
    public UserAccount findByUsername(String normalizedUsername) {
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, normalizedUsername)
                .last("LIMIT 1"));
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_EMAIL, key = "#normalizedEmail", unless = "#result == null")
    /**
     * 按邮箱查询用户，要求传入已归一化为小写的 normalizedEmail。
     */
    public UserAccount findByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, normalizedEmail)
                .last("LIMIT 1"));
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_LOGIN, key = "#normalizedLoginKey", unless = "#result == null")
    /**
     * 同时按用户名或邮箱匹配用户，用于登录输入兼容两种登录名场景。
     */
    public UserAccount findByUsernameOrEmail(String normalizedLoginKey) {
        if (normalizedLoginKey == null || normalizedLoginKey.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .and(q -> q.eq(UserAccount::getUsername, normalizedLoginKey)
                        .or()
                        .eq(UserAccount::getEmail, normalizedLoginKey))
                .last("LIMIT 1"));
    }

    /**
     * 清除该用户全部键空间下的缓存条目，避免修改账号信息后读取陈旧数据。
     */
    public void evictUser(UserAccount user) {
        if (user == null) {
            return;
        }
        evict(CacheConfig.USER_ACCOUNT_BY_ID, user.getId());
        evict(CacheConfig.USER_ACCOUNT_BY_USERNAME, normalize(user.getUsername()));
        String email = normalizeEmail(user.getEmail());
        evict(CacheConfig.USER_ACCOUNT_BY_EMAIL, email);
        evict(CacheConfig.USER_ACCOUNT_BY_LOGIN, normalize(user.getUsername()));
        evict(CacheConfig.USER_ACCOUNT_BY_LOGIN, email);
    }

    private void evict(String cacheName, Object key) {
        if (key == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String email) {
        return normalize(email).toLowerCase();
    }
}

