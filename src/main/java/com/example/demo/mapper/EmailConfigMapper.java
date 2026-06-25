package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.EmailConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮箱配置Mapper
 */
@Mapper
public interface EmailConfigMapper extends BaseMapper<EmailConfig> {
}