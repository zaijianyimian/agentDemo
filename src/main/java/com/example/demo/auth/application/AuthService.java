package com.example.demo.auth.application;

import com.example.demo.auth.dto.AuthTokenResponse;
import com.example.demo.auth.dto.FaceStatusResponse;
import com.example.demo.auth.dto.EmailCodeSendResponse;
import com.example.demo.auth.dto.AuthUserProfile;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.domain.UserAccount;
import com.example.demo.auth.persistence.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
/**
 * 用户认证服务。负责用户名密码登录、邮箱验证码登录、人脸识别登录的统一处理，
 * 以及 JWT 令牌的签发与版本校验。
 */
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final EmailCodeService emailCodeService;
    private final UserAccountCacheService userAccountCacheService;
    private final FaceAuthService faceAuthService;
    private final PreAuthTokenService preAuthTokenService;

    @Transactional(rollbackFor = Exception.class)
    /**
     * 新用户注册。事务内校验唯一性后写入账号并触发注册邮件验证码发送。
     */
    public EmailCodeSendResponse register(RegisterRequest request) {
        String username = normalize(request.getUsername());
        String email = normalizeEmail(request.getEmail());

        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (findByEmail(email) != null) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        UserAccount user = UserAccount.builder()
                .username(username)
                .email(email)
                .displayName((request.getDisplayName() == null || request.getDisplayName().isBlank())
                        ? username
                        : normalize(request.getDisplayName()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(AuthConstants.DEFAULT_USER_ROLE)
                .enabled(true)
                .emailVerified(false)
                .faceAuthEnabled(false)
                .tokenVersion(0)
                .build();

        userAccountMapper.insert(user);
        int cooldown = emailCodeService.sendRegisterCode(user.getEmail());
        return EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("注册成功，请查收邮箱验证码完成确认后登录")
                .build();
    }

    /**
     * 用户名或邮箱 + 密码登录。校验邮箱已验证且密码正确后下发令牌或人脸挑战。
     */
    public AuthTokenResponse loginByPassword(String usernameOrEmail, String password) {
        UserAccount user = requireActiveUser(findByUsernameOrEmail(usernameOrEmail));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("邮箱未验证，请先使用邮箱验证码登录完成确认");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return issueTokensOrChallenge(user);
    }

    /**
     * 判断数据库中是否已存在任何用户账号。
     */
    public boolean hasUsers() {
        Long count = userAccountMapper.selectCount(null);
        return count != null && count > 0;
    }

    /**
     * 向指定邮箱发送登录验证码。返回当前冷却时间。
     */
    public int sendLoginCode(String email) {
        UserAccount user = requireActiveUser(findByEmail(email));
        return emailCodeService.sendLoginCode(user.getEmail());
    }

    /**
     * 邮箱验证码登录。校验后会顺带将邮箱标记为已验证并签发令牌。
     */
    public AuthTokenResponse loginByEmailCode(String email, String code) {
        UserAccount user = requireActiveUser(findByEmail(email));

        boolean ok = emailCodeService.verifyAndConsumeAuthCode(user.getEmail(), code);
        if (!ok) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        user.setEmailVerified(true);
        userAccountMapper.updateById(user);
        userAccountCacheService.evictUser(user);
        return issueTokensOrChallenge(user);
    }

    /**
     * 用 refreshToken 换发新的令牌对。会校验 tokenVersion 以确保旧令牌已失效。
     */
    public AuthTokenResponse refreshToken(String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtTokenService.decode(refreshToken);
        } catch (JwtException ex) {
            throw new IllegalArgumentException("refreshToken 无效");
        }
        String type = jwt.getClaimAsString("type");
        if (!AuthConstants.TOKEN_TYPE_REFRESH.equals(type)) {
            throw new IllegalArgumentException("refreshToken 无效");
        }

        Long userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new IllegalArgumentException("refreshToken 无效");
        }

        UserAccount user = requireActiveUser(userAccountCacheService.findById(userId));
        validateTokenVersion(jwt, user);

        return issueTokens(user);
    }

    /**
     * 返回当前用户的公开资料信息。
     */
    public AuthUserProfile getProfile(Long userId) {
        UserAccount user = userAccountCacheService.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toProfile(user);
    }

    /**
     * 修改密码。需校验当前密码正确，并自增 tokenVersion 让所有旧令牌失效。
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccount user = userAccountCacheService.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        bumpTokenVersion(user);
        userAccountMapper.updateById(user);
        userAccountCacheService.evictUser(user);
    }

    /**
     * 发送重置密码验证码。为避免泄露邮箱是否存在，未注册邮箱同样抛出与正常分支一致的提示。
     */
    public int sendResetPasswordCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = findByEmail(normalizedEmail);
        if (user == null) {
            // 为了安全，不暴露用户是否存在，但也不发送邮件
            throw new IllegalArgumentException("如果该邮箱已注册，验证码将发送到邮箱");
        }
        return emailCodeService.sendResetPasswordCode(normalizedEmail);
    }

    /**
     * 校验重置密码验证码并更新密码。成功后自增 tokenVersion 使所有旧令牌失效。
     */
    public void resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = requireActiveUser(findByEmail(normalizedEmail));

        boolean ok = emailCodeService.verifyAndConsumeResetCode(normalizedEmail, code);
        if (!ok) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        bumpTokenVersion(user);
        userAccountMapper.updateById(user);
        userAccountCacheService.evictUser(user);
    }

    /**
     * 用户登出。通过自增 tokenVersion 强制该用户现存令牌失效。
     */
    public void logout(Long userId) {
        UserAccount user = requireActiveUser(userAccountCacheService.findById(userId));
        bumpTokenVersion(user);
        userAccountMapper.updateById(user);
        userAccountCacheService.evictUser(user);
    }

    /**
     * 从 JWT 中解析 userId 声明，若缺失则抛出非法参数异常。
     */
    public Long extractUserIdFromJwt(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (!(claim instanceof Number number)) {
            throw new IllegalArgumentException("无效身份令牌");
        }
        long userId;
        try {
            if (number instanceof java.math.BigInteger integer) {
                userId = integer.longValueExact();
            } else if (number instanceof java.math.BigDecimal decimal) {
                userId = decimal.longValueExact();
            } else if (number instanceof Double || number instanceof Float) {
                double value = number.doubleValue();
                if (!Double.isFinite(value) || value != Math.rint(value)) {
                    throw new ArithmeticException("non-integral userId");
                }
                userId = java.math.BigDecimal.valueOf(value).longValueExact();
            } else {
                userId = number.longValue();
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("无效身份令牌", exception);
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("无效身份令牌");
        }
        return userId;
    }

    /**
     * 为指定用户签发令牌对（不经过两步验证）。会在内部校验账号状态。
     */
    public AuthTokenResponse issueTokensForUser(UserAccount user) {
        return issueTokens(requireActiveUser(user));
    }

    /**
     * 根据账号是否启用二次验证选择签发令牌或下发预认证挑战。
     */
    public AuthTokenResponse issueTokensOrChallenge(UserAccount user) {
        UserAccount activeUser = requireActiveUser(user);
        if (faceAuthService.isFaceRequired(activeUser)) {
            FaceStatusResponse status = faceAuthService.getStatus(activeUser.getId());
            if (!status.isEnrolled() || !status.isEnabled()) {
                throw new IllegalArgumentException("账号已开启人脸二次验证，但未绑定可用人脸，请联系管理员处理");
            }
            PreAuthTokenService.PreAuthSession session = preAuthTokenService.issue(activeUser.getId());
            return AuthTokenResponse.builder()
                    .requiresSecondFactor(true)
                    .preAuthToken(session.getToken())
                    .preAuthExpiresIn(preAuthTokenService.getExpiresInSeconds())
                    .user(toProfile(activeUser))
                    .build();
        }
        markLoginSuccess(activeUser);
        return issueTokens(activeUser);
    }

    /**
     * 使用预认证令牌 + 人脸图片完成第二步验证后签发最终令牌。
     */
    public AuthTokenResponse verifyFaceLogin(String preAuthToken, String imageBase64) {
        PreAuthTokenService.PreAuthSession session = preAuthTokenService.consume(preAuthToken);
        UserAccount user = requireActiveUser(userAccountCacheService.findById(session.getUserId()));
        faceAuthService.verifyForLogin(user.getId(), imageBase64);
        markLoginSuccess(user);
        return issueTokens(user);
    }

    /**
     * 返回当前用户人脸绑定及二次验证状态。
     */
    public FaceStatusResponse getFaceStatus(Long userId) {
        return faceAuthService.getStatus(userId);
    }

    /**
     * 绑定或重新绑定用户人脸。会清理缓存避免 tokenVersion 信息陈旧。
     */
    public FaceStatusResponse registerFace(Long userId, String imageBase64) {
        FaceStatusResponse response = faceAuthService.register(userId, imageBase64);
        UserAccount user = userAccountCacheService.findById(userId);
        if (user != null) {
            userAccountCacheService.evictUser(user);
        }
        return response;
    }

    /**
     * 切换人脸二次验证开关。开启前必须已绑定人脸。
     */
    public FaceStatusResponse updateFaceRequired(Long userId, boolean required) {
        FaceStatusResponse response = faceAuthService.updateRequired(userId, required);
        UserAccount user = userAccountCacheService.findById(userId);
        if (user != null) {
            userAccountCacheService.evictUser(user);
        }
        return response;
    }

    /**
     * 校验 JWT 中携带的 tokenVersion 是否与用户当前版本一致，不一致视为已失效。
     */
    public void validateTokenVersion(Jwt jwt, UserAccount user) {
        Object claim = jwt.getClaim("tokenVersion");
        if (!(claim instanceof Number tokenVersion)) {
            throw new IllegalArgumentException("令牌版本无效");
        }
        int expectedVersion = safeTokenVersion(user);
        int tokenValue;
        try {
            if (tokenVersion instanceof java.math.BigInteger integer) {
                tokenValue = integer.intValueExact();
            } else if (tokenVersion instanceof java.math.BigDecimal decimal) {
                tokenValue = decimal.intValueExact();
            } else if (tokenVersion instanceof Double || tokenVersion instanceof Float) {
                double value = tokenVersion.doubleValue();
                if (!Double.isFinite(value) || value != Math.rint(value)) {
                    throw new ArithmeticException("non-integral token version");
                }
                tokenValue = Math.toIntExact((long) value);
            } else {
                long value = tokenVersion.longValue();
                tokenValue = Math.toIntExact(value);
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("令牌版本无效", exception);
        }
        if (tokenValue != expectedVersion) {
            throw new IllegalArgumentException("令牌已失效，请重新登录");
        }
    }

    public void validateTokenVersion(Jwt jwt) {
        if (!AuthConstants.TOKEN_TYPE_ACCESS.equals(jwt.getClaimAsString("type"))) {
            throw new IllegalArgumentException("令牌类型无效");
        }
        Long userId = extractUserIdFromJwt(jwt);
        UserAccount user = requireActiveUser(userAccountCacheService.findById(userId));
        validateTokenVersion(jwt, user);
    }

    private AuthTokenResponse issueTokens(UserAccount user) {
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(user);
        return AuthTokenResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenService.getAccessTokenExpiresInSeconds())
                .user(toProfile(user))
                .requiresSecondFactor(false)
                .build();
    }

    private AuthUserProfile toProfile(UserAccount user) {
        return AuthUserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private UserAccount requireActiveUser(UserAccount user) {
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("账号不存在或已禁用");
        }
        return user;
    }

    private void markLoginSuccess(UserAccount user) {
        user.setLastLoginTime(LocalDateTime.now());
        userAccountMapper.updateById(user);
        userAccountCacheService.evictUser(user);
    }

    private void bumpTokenVersion(UserAccount user) {
        user.setTokenVersion(safeTokenVersion(user) + 1);
    }

    private UserAccount findByUsername(String username) {
        return userAccountCacheService.findByUsername(normalize(username));
    }

    private UserAccount findByEmail(String email) {
        return userAccountCacheService.findByEmail(normalizeEmail(email));
    }

    private UserAccount findByUsernameOrEmail(String usernameOrEmail) {
        String normalized = normalize(usernameOrEmail);
        if (normalized.contains("@")) {
            normalized = normalized.toLowerCase();
        }
        return userAccountCacheService.findByUsernameOrEmail(normalized);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String email) {
        return normalize(email).toLowerCase();
    }

    private int safeTokenVersion(UserAccount user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
