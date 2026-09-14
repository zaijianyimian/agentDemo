package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.DispatchResultOutbox;
import com.example.demo.dispatch.domain.OwnedOutboxRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DispatchResultOutboxMapper extends BaseMapper<DispatchResultOutbox> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT user_id AS userId, id AS outboxId FROM dispatch_result_outbox "
            + "WHERE available_at <= #{now} AND (status IN ('PENDING','RETRY') "
            + "OR (status = 'PROCESSING' AND lease_until < #{now})) "
            + "ORDER BY available_at, id LIMIT #{limit}")
    List<OwnedOutboxRef> selectReadyForInternalRelay(@Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    @Update("UPDATE dispatch_result_outbox SET status = 'PROCESSING', lease_until = #{leaseUntil}, "
            + "updated_at = NOW() WHERE user_id = #{userId} AND id = #{id} "
            + "AND available_at <= #{now} AND (status IN ('PENDING','RETRY') "
            + "OR (status = 'PROCESSING' AND lease_until < #{now}))")
    int claimLease(@Param("userId") long userId, @Param("id") long id,
                   @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE dispatch_result_outbox SET status = 'PUBLISHED', published_at = NOW(), "
            + "lease_until = NULL, last_error = NULL, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND id = #{id} AND status = 'PROCESSING'")
    int markPublished(@Param("userId") long userId, @Param("id") long id);

    @Update("UPDATE dispatch_result_outbox SET status = 'RETRY', publish_attempts = publish_attempts + 1, "
            + "available_at = #{availableAt}, lease_until = NULL, last_error = #{lastError}, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND id = #{id} AND status = 'PROCESSING'")
    int markRetry(@Param("userId") long userId, @Param("id") long id,
                  @Param("availableAt") LocalDateTime availableAt,
                  @Param("lastError") String lastError);
}
