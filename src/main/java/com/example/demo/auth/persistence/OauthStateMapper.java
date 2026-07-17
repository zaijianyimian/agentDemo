package com.example.demo.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.domain.OauthState;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth state 表 MyBatis-Plus Mapper,负责 oauth_state 临时凭证的存取与清理。
 * <p>
 * 由 OAuthStateService 调用,用于授权发起时写入、回调时校验并删除过期凭证。
 */
@Mapper
public interface OauthStateMapper extends BaseMapper<OauthState> {
}
