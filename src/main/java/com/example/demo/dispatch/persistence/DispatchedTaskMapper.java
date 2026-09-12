package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.DispatchedTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 派发任务 Mapper。
 */
@Mapper
public interface DispatchedTaskMapper extends BaseMapper<DispatchedTask> {

    /**
     * 抢占任务：仅当当前状态仍为 PENDING 时才将其改为 RUNNING。
     *
     * @return 受影响行数；0 表示抢占失败（任务已被其他 worker 领取，或状态已变更）。
     */
    @Update("UPDATE dispatched_task SET status = 'RUNNING', executor_used = #{executor}, "
            + "updated_at = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int claimRunning(@Param("id") Long id, @Param("executor") String executor);
}
