package com.example.demo.email.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.email.domain.EmailAttachmentAnalysis;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 邮件附件解析结果的数据访问入口。
 *
 * <p>{@code user_id} 由 TenantLine 自动追加；业务唯一键额外包含 {@code email_config_id}，
 * 避免不同邮箱或不同用户的相同 Message-ID/文件名发生碰撞。</p>
 */
@Mapper
public interface EmailAttachmentAnalysisMapper extends BaseMapper<EmailAttachmentAnalysis> {

    @Select("SELECT * FROM email_attachment_analysis "
            + "WHERE email_config_id = #{emailConfigId} AND message_id = #{messageId} ORDER BY id ASC")
    List<EmailAttachmentAnalysis> selectByMessageId(
            @Param("emailConfigId") Long emailConfigId,
            @Param("messageId") String messageId);

    @Select("SELECT * FROM email_attachment_analysis "
            + "WHERE email_config_id = #{emailConfigId} AND file_name = #{fileName} "
            + "AND message_id = #{messageId} LIMIT 1")
    EmailAttachmentAnalysis selectByMessageIdAndFileName(
            @Param("emailConfigId") Long emailConfigId,
            @Param("messageId") String messageId,
            @Param("fileName") String fileName);

    @Delete("DELETE FROM email_attachment_analysis "
            + "WHERE email_config_id = #{emailConfigId} AND message_id = #{messageId}")
    int deleteByMessageId(
            @Param("emailConfigId") Long emailConfigId,
            @Param("messageId") String messageId);
}
