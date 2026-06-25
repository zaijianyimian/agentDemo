package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ChatHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天记录 Mapper
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 查询指定平台的所有会话ID
     */
    @Select("SELECT DISTINCT session_id FROM chat_history WHERE platform = #{platform} ORDER BY session_id")
    List<String> findSessionIdsByPlatform(@Param("platform") String platform);

    /**
     * 查询指定会话的所有消息，按时间排序
     */
    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId} ORDER BY message_time ASC")
    List<ChatHistory> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询未向量化且关联了助手的消息
     */
    @Select("SELECT * FROM chat_history WHERE vectorized = 0 AND assistant_id IS NOT NULL AND assistant_id = #{assistantId} LIMIT #{limit}")
    List<ChatHistory> findUnvectorizedByAssistantId(@Param("assistantId") Long assistantId, @Param("limit") int limit);

    /**
     * 统计指定助手关联的消息数量
     */
    @Select("SELECT COUNT(*) FROM chat_history WHERE assistant_id = #{assistantId}")
    int countByAssistantId(@Param("assistantId") Long assistantId);

    /**
     * 批量更新向量化状态
     * 使用foreach构建安全的IN条件
     */
    @Update("<script>" +
            "UPDATE chat_history SET vectorized = 1, update_time = NOW() WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateVectorized(@Param("ids") List<Long> ids);

    /**
     * 删除指定助手关联的所有聊天记录
     */
    @Delete("DELETE FROM chat_history WHERE assistant_id = #{assistantId}")
    int deleteByAssistantId(@Param("assistantId") Long assistantId);
}