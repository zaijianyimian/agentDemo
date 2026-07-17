package com.example.demo.task.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.task.domain.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 负责计划任务的持久化访问，提供基础数据操作与启用任务查询能力。
 * <p>
 * 供任务应用服务读取和管理计划任务数据。
 */
@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {

    @Select("SELECT * FROM scheduled_task WHERE enabled = 1")
    List<ScheduledTask> selectEnabled();
}
