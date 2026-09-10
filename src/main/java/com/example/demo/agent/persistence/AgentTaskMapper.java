package com.example.demo.agent.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.agent.domain.AgentTask;
import org.apache.ibatis.annotations.Mapper;

/** Agent 业务任务 Mapper。 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
