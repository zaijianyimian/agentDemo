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
 * OAuth 授权流程中的 state 临时凭证,用于防止 CSRF 攻击并回传回调路径。
 * <p>
 * 在 OAuth 发起时生成,回调时校验并清除;redirectPath 用于登录完成后跳转回原页面。
 */
@TableName("oauth_state")
public class OauthState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String state;

    private String redirectPath;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
