package com.example.demo.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌内省结果。
 *
 * <p>供 Python Agent Engine 调用。Java 是令牌的唯一权威：签名、有效期、令牌类型、
 * 账号状态与 tokenVersion 全部由 Java 判定，Python 不自行解析令牌，也不接受浏览器
 * 自报的用户身份。</p>
 *
 * <p>字段使用 snake_case，与 Python 侧的解析契约保持一致。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospectionResponse {

    /** 令牌是否有效。 */
    private Boolean valid;

    /** 令牌确认的用户 ID；无效时为 null。 */
    @JsonProperty("user_id")
    private Long userId;

    /** 令牌过期时间戳（秒）；无效时为 null。 */
    @JsonProperty("expires_at")
    private Long expiresAt;
}
