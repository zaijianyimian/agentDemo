package com.example.demo.email.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.OwnedEmailConfigRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 邮箱配置Mapper
 */
@Mapper
public interface EmailConfigMapper extends BaseMapper<EmailConfig> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM email_config WHERE enabled = 1 ORDER BY id")
    List<EmailConfig> selectEnabledForInternalScan();

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT user_id AS userId, id AS configId FROM email_config "
            + "WHERE id = #{configId} AND enabled = 1")
    OwnedEmailConfigRef selectOwnedRefForInternalValidation(@Param("configId") long configId);
}
