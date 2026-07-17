package com.example.demo.schedule.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.schedule.domain.ScheduleEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日程事件Mapper
 */
@Mapper
public interface ScheduleEventMapper extends BaseMapper<ScheduleEvent> {
}