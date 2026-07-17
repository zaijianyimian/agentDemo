package com.example.demo.auth.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.auth.domain.AuthEmailCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮箱验证码表 MyBatis-Plus Mapper,负责 auth_email_code 表的读写。
 * <p>
 * 由 EmailCodeService 调用,完成验证码写入、按邮箱+用途查询最新记录、标记已使用等操作。
 */
@Mapper
public interface AuthEmailCodeMapper extends BaseMapper<AuthEmailCode> {
}
