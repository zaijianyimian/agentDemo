package com.example.demo.dispatch.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.dispatch.domain.ConsumedEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConsumedEventMapper extends BaseMapper<ConsumedEvent> {

    @Insert("INSERT INTO consumed_event "
            + "(user_id, consumer_name, event_id, status, attempt, resource_type, resource_id) "
            + "VALUES (#{userId}, #{consumerName}, #{eventId}, 'PROCESSING', 1, #{resourceType}, #{resourceId}) "
            + "ON DUPLICATE KEY UPDATE "
            + "attempt = IF(status = 'FAILED', attempt + 1, attempt), "
            + "status = IF(status = 'FAILED', 'PROCESSING', status), "
            + "error_message = IF(status = 'FAILED', NULL, error_message), updated_at = CURRENT_TIMESTAMP")
    int tryAcquire(
            @Param("userId") long userId,
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    @Update("UPDATE consumed_event SET status = 'COMMITTED', error_message = NULL, "
            + "resource_type = #{resourceType}, resource_id = #{resourceId}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE user_id = #{userId} AND consumer_name = #{consumerName} AND event_id = #{eventId} "
            + "AND status = 'PROCESSING'")
    int markCommitted(
            @Param("userId") long userId,
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    @Insert("INSERT INTO consumed_event "
            + "(user_id, consumer_name, event_id, status, attempt, resource_type, resource_id, error_message) "
            + "VALUES (#{userId}, #{consumerName}, #{eventId}, 'FAILED', 1, #{resourceType}, #{resourceId}, #{error}) "
            + "ON DUPLICATE KEY UPDATE "
            + "status = IF(status = 'COMMITTED', status, 'FAILED'), "
            + "error_message = IF(status = 'COMMITTED', error_message, #{error}), updated_at = CURRENT_TIMESTAMP")
    int recordFailure(
            @Param("userId") long userId,
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("error") String error);
}
