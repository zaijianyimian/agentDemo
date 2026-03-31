package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.config.CacheConfig;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccountCacheService {

    private final UserAccountMapper userAccountMapper;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_ID, key = "#id", unless = "#result == null")
    public UserAccount findById(Long id) {
        if (id == null) {
            return null;
        }
        return userAccountMapper.selectById(id);
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_USERNAME, key = "#normalizedUsername", unless = "#result == null")
    public UserAccount findByUsername(String normalizedUsername) {
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, normalizedUsername)
                .last("LIMIT 1"));
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_EMAIL, key = "#normalizedEmail", unless = "#result == null")
    public UserAccount findByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, normalizedEmail)
                .last("LIMIT 1"));
    }

    @Cacheable(cacheNames = CacheConfig.USER_ACCOUNT_BY_LOGIN, key = "#normalizedLoginKey", unless = "#result == null")
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

