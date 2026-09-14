package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.PushConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 推送配置 Mapper（单行表）。
 */
@Mapper
public interface PushConfigMapper extends BaseMapper<PushConfig> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM push_config ORDER BY user_id")
    List<PushConfig> selectAllForInternalScan();
}
