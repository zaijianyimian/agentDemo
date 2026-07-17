package com.example.demo.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.domain.OauthAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方 OAuth 账号绑定表 MyBatis-Plus Mapper,提供 oauth_account 的基础 CRUD。
 * <p>
 * 由 GithubOAuthService 调用,完成外部账号与系统用户的绑定、查询与解除绑定。
 */
@Mapper
public interface OauthAccountMapper extends BaseMapper<OauthAccount> {
}
