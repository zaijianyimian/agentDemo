package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.PushConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推送配置 Mapper（单行表）。
 */
@Mapper
public interface PushConfigMapper extends BaseMapper<PushConfig> {
}
