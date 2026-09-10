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
 * <p>{@code user_id} 由 TenantLine 自动追加；写入幂等额外包含 {@code email_config_id}，
 * 避免同一用户不同邮箱的相同 Message-ID/文件名发生碰撞。</p>
 */
@Mapper
public interface EmailAttachmentAnalysisMapper extends BaseMapper<EmailAttachmentAnalysis> {

    /**
     * 查询当前用户下同 Message-ID 的全部附件分析；可能来自用户自己的多个邮箱。
     */
    @Select("SELECT * FROM email_attachment_analysis WHERE message_id = #{messageId} ORDER BY id ASC")
    List<EmailAttachmentAnalysis> selectByMessageId(String messageId);

    /**
     * 在指定邮箱内查找一条附件分析记录，用于幂等覆盖。
     */
    @Select("SELECT * FROM email_attachment_analysis "
            + "WHERE email_config_id = #{emailConfigId} AND file_name = #{fileName} "
            + "AND message_id = #{messageId} LIMIT 1")
    EmailAttachmentAnalysis selectByMessageIdAndFileName(
            @Param("emailConfigId") Long emailConfigId,
            @Param("messageId") String messageId,
            @Param("fileName") String fileName);

    /**
     * 删除指定邮箱内某封邮件的分析结果。
     */
    @Delete("DELETE FROM email_attachment_analysis "
            + "WHERE email_config_id = #{emailConfigId} AND message_id = #{messageId}")
    int deleteByMessageId(
            @Param("emailConfigId") Long emailConfigId,
            @Param("messageId") String messageId);
}
