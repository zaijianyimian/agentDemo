package com.example.demo.task.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.task.domain.JobLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务执行日志的数据访问层。
 */
@Mapper
public interface JobLogMapper extends BaseMapper<JobLog> {

    /**
     * 查询某任务的最近 N 条日志，按触发时间倒序。
     */
    default List<JobLog> selectRecentByJobId(Long jobId, int limit) {
        return selectPage(new Page<>(1, limit, false),
                        new QueryWrapper<JobLog>()
                                .eq("job_id", jobId)
                                .orderByDesc("trigger_time"))
                .getRecords();
    }

    /**
     * 查询某任务的全部日志（分页），按触发时间倒序。
     */
    default com.baomidou.mybatisplus.core.metadata.IPage<JobLog> pageByJobId(Long jobId, int page, int size) {
        return selectPage(new Page<>(page, size),
                new QueryWrapper<JobLog>()
                        .eq("job_id", jobId)
                        .orderByDesc("trigger_time"));
    }

    /**
     * 删除某任务的所有日志（任务删除时级联清理）。
     */
    default int deleteByJobId(Long jobId) {
        return delete(new QueryWrapper<JobLog>().eq("job_id", jobId));
    }
}