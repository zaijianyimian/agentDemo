package com.example.demo.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户主账号表 MyBatis-Plus Mapper,提供对 user_account 表的基础 CRUD 能力。
 * <p>
 * 由 AuthService、UserAccountCacheService 等调用,完成账号查询、tokenVersion 自增与状态字段更新等持久化操作。
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
