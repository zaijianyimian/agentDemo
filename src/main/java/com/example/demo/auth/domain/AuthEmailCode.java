package com.example.demo.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 邮件验证码实体,记录向用户邮箱发送的验证码及其用途、有效期和使用状态。
 * <p>
 * 与 EmailCodeService 配合:用于登录、重置密码等场景的一次性验证码下发与校验。
 */
@TableName("auth_email_code")
public class AuthEmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String code;

    private String purpose;

    private Boolean used;

    private LocalDateTime sendTime;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
