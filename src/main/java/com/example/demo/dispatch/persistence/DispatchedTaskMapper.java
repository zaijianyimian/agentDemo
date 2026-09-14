package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.OwnedTaskRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 派发任务 Mapper。
 */
@Mapper
public interface DispatchedTaskMapper extends BaseMapper<DispatchedTask> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT user_id AS userId, id AS taskId, version, attempt FROM dispatched_task "
            + "WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT #{limit}")
    List<OwnedTaskRef> selectPendingForInternalScan(@Param("limit") int limit);

    /**
     * 抢占任务：仅当当前状态仍为 PENDING 时才将其改为 RUNNING。
     *
     * @return 受影响行数；0 表示抢占失败（任务已被其他 worker 领取，或状态已变更）。
     */
    @Update("UPDATE dispatched_task SET status = 'RUNNING', version = version + 1, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND id = #{id} AND status = 'PENDING' AND version = #{version}")
    int claimRunning(@Param("userId") long userId, @Param("id") long id,
                     @Param("version") long version);

    @Update("UPDATE dispatched_task SET status = 'FAILED', error_code = #{errorCode}, "
            + "error_message = #{errorMessage}, finished_at = NOW(), updated_at = NOW(), version = version + 1 "
            + "WHERE user_id = #{userId} AND id = #{id} AND status = 'RUNNING' "
            + "AND version = #{version} AND attempt = #{attempt}")
    int failCurrentAttempt(@Param("userId") long userId, @Param("id") long id,
                           @Param("version") long version, @Param("attempt") int attempt,
                           @Param("errorCode") String errorCode,
                           @Param("errorMessage") String errorMessage);

    @Update("UPDATE dispatched_task SET status = 'CANCELLED', finished_at = NOW(), updated_at = NOW(), "
            + "version = version + 1 WHERE user_id = #{userId} AND id = #{id} "
            + "AND status = 'PENDING' AND version = #{version}")
    int cancelPending(@Param("userId") long userId, @Param("id") long id,
                      @Param("version") long version);

    @Update("UPDATE dispatched_task SET status = 'PENDING', retries = 0, attempt = attempt + 1, "
            + "finished_at = NULL, push_status = 'pending', error_code = NULL, error_message = NULL, "
            + "updated_at = NOW(), version = version + 1 WHERE user_id = #{userId} AND id = #{id} "
            + "AND status IN ('DONE','FAILED') AND version = #{version}")
    int rerunTerminal(@Param("userId") long userId, @Param("id") long id,
                      @Param("version") long version);
}
