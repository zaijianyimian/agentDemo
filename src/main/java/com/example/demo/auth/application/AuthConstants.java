package com.example.demo.auth.application;

/**
 * 认证模块常量集中定义。包括 JWT 类型、邮箱验证码用途、默认用户角色等。
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_RESET_PASSWORD = "RESET_PASSWORD";
    public static final String DEFAULT_USER_ROLE = "USER";
}
