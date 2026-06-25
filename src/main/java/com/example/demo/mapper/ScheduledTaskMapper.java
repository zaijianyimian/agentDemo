package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {

    @Select("SELECT * FROM scheduled_task WHERE enabled = 1")
    List<ScheduledTask> selectEnabled();

    @Select("SELECT * FROM scheduled_task WHERE task_type = #{taskType} AND enabled = 1")
    List<ScheduledTask> selectByType(String taskType);
}