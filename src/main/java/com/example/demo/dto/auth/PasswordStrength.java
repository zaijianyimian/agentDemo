package com.example.demo.dto.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 密码强度验证注解
 * 要求密码包含：
 * - 至少一个大写字母
 * - 至少一个小写字母
 * - 至少一个数字
 * - 至少一个特殊字符
 * - 长度在 8-64 之间
 */
@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordStrength {

    String message() default "密码必须包含大小写字母、数字和特殊字符，长度8-64位";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}