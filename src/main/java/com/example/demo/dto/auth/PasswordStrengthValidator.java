package com.example.demo.dto.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 密码强度验证器
 * 检查密码是否符合安全要求：
 * - 至少一个大写字母 (A-Z)
 * - 至少一个小写字母 (a-z)
 * - 至少一个数字 (0-9)
 * - 至少一个特殊字符 (!@#$%^&*()_+-=[]{}|;:,.<>?)
 * - 长度在 8-64 之间
 */
public class PasswordStrengthValidator implements ConstraintValidator<PasswordStrength, String> {

    // 密码复杂度正则表达式
    private static final Pattern UPPER_CASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWER_CASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?]");

    // 允许的字符范围（防止注入攻击）
    private static final Pattern ALLOWED_CHARS = Pattern.compile("[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?]+");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        // 长度检查
        if (password.length() < 8 || password.length() > 64) {
            return false;
        }

        // 检查是否只包含允许的字符
        if (!ALLOWED_CHARS.matcher(password).matches()) {
            return false;
        }

        // 检查各种字符类型
        boolean hasUpper = UPPER_CASE.matcher(password).find();
        boolean hasLower = LOWER_CASE.matcher(password).find();
        boolean hasDigit = DIGIT.matcher(password).find();
        boolean hasSpecial = SPECIAL_CHAR.matcher(password).find();

        // 必须包含所有类型
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}