package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ScheduleEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日程事件Mapper
 */
@Mapper
public interface ScheduleEventMapper extends BaseMapper<ScheduleEvent> {
}