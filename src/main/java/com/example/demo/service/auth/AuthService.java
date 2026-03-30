package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.auth.AuthTokenResponse;
import com.example.demo.dto.auth.EmailCodeSendResponse;
import com.example.demo.dto.auth.AuthUserProfile;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final EmailCodeService emailCodeService;

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
                .tokenVersion(0)
                .build();

        userAccountMapper.insert(user);
        int cooldown = emailCodeService.sendRegisterCode(user.getEmail());
        return EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("注册成功，请查收邮箱验证码完成确认后登录")
                .build();
    }

    public AuthTokenResponse loginByPassword(String usernameOrEmail, String password) {
        UserAccount user = requireActiveUser(findByUsernameOrEmail(usernameOrEmail));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("邮箱未验证，请先使用邮箱验证码登录完成确认");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        markLoginSuccess(user);
        return issueTokens(user);
    }

    public int sendLoginCode(String email) {
        UserAccount user = requireActiveUser(findByEmail(email));
        return emailCodeService.sendLoginCode(user.getEmail());
    }

    public AuthTokenResponse loginByEmailCode(String email, String code) {
        UserAccount user = requireActiveUser(findByEmail(email));

        boolean ok = emailCodeService.verifyAndConsumeAuthCode(user.getEmail(), code);
        if (!ok) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }

        user.setEmailVerified(true);
        markLoginSuccess(user);
        return issueTokens(user);
    }

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

        UserAccount user = requireActiveUser(userAccountMapper.selectById(userId));
        validateTokenVersion(jwt, user);

        return issueTokens(user);
    }

    public AuthUserProfile getProfile(Long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toProfile(user);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        bumpTokenVersion(user);
        userAccountMapper.updateById(user);
    }

    public void logout(Long userId) {
        UserAccount user = requireActiveUser(userAccountMapper.selectById(userId));
        bumpTokenVersion(user);
        userAccountMapper.updateById(user);
    }

    public Long extractUserIdFromJwt(Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new IllegalArgumentException("无效身份令牌");
        }
        return userId;
    }

    public AuthTokenResponse issueTokensForUser(UserAccount user) {
        return issueTokens(requireActiveUser(user));
    }

    public void validateTokenVersion(Jwt jwt, UserAccount user) {
        Number tokenVersion = jwt.getClaim("tokenVersion");
        int expectedVersion = safeTokenVersion(user);
        int tokenValue = tokenVersion == null ? 0 : tokenVersion.intValue();
        if (tokenValue != expectedVersion) {
            throw new IllegalArgumentException("令牌已失效，请重新登录");
        }
    }

    public void validateTokenVersion(Jwt jwt) {
        Long userId = extractUserIdFromJwt(jwt);
        UserAccount user = requireActiveUser(userAccountMapper.selectById(userId));
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
    }

    private void bumpTokenVersion(UserAccount user) {
        user.setTokenVersion(safeTokenVersion(user) + 1);
    }

    private UserAccount findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, normalize(username))
                .last("LIMIT 1"));
    }

    private UserAccount findByEmail(String email) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, normalizeEmail(email))
                .last("LIMIT 1"));
    }

    private UserAccount findByUsernameOrEmail(String usernameOrEmail) {
        String value = normalize(usernameOrEmail);
        String lowerEmail = value.toLowerCase();
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .and(q -> q.eq(UserAccount::getUsername, value).or().eq(UserAccount::getEmail, lowerEmail))
                .last("LIMIT 1"));
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
