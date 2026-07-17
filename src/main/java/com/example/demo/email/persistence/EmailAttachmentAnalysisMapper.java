package com.example.demo.email.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.email.domain.EmailAttachmentAnalysis;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 邮件附件解析结果的数据访问入口。
 */
@Mapper
public interface EmailAttachmentAnalysisMapper extends BaseMapper<EmailAttachmentAnalysis> {

    @Select("SELECT * FROM email_attachment_analysis WHERE message_id = #{messageId} ORDER BY id ASC")
    List<EmailAttachmentAnalysis> selectByMessageId(String messageId);

    @Select("SELECT * FROM email_attachment_analysis WHERE file_name = #{fileName} AND message_id = #{messageId} LIMIT 1")
    EmailAttachmentAnalysis selectByMessageIdAndFileName(String messageId, String fileName);

    @Delete("DELETE FROM email_attachment_analysis WHERE message_id = #{messageId}")
    int deleteByMessageId(String messageId);
}
