package com.example.demo.email.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.email.domain.EmailListenerState;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮箱监听状态 MyBatis-Plus Mapper。
 * <p>
 * 对 {@code email_listener_state} 表的 CRUD 入口，由 {@link com.example.demo.email.application.EmailListenerStateService} 调用。
 */
@Mapper
public interface EmailListenerStateMapper extends BaseMapper<EmailListenerState> {
}
