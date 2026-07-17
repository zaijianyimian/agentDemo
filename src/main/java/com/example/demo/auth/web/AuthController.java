package com.example.demo.auth.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.auth.dto.AuthTokenResponse;
import com.example.demo.auth.dto.AuthUserProfile;
import com.example.demo.auth.dto.ChangePasswordRequest;
import com.example.demo.auth.dto.EmailCodeLoginRequest;
import com.example.demo.auth.dto.EmailCodeSendRequest;
import com.example.demo.auth.dto.EmailCodeSendResponse;
import com.example.demo.auth.dto.FaceLoginVerifyRequest;
import com.example.demo.auth.dto.FaceRegisterRequest;
import com.example.demo.auth.dto.FaceRequiredRequest;
import com.example.demo.auth.dto.FaceStatusResponse;
import com.example.demo.auth.dto.GithubAuthorizeResponse;
import com.example.demo.auth.dto.GithubExchangeRequest;
import com.example.demo.auth.dto.GithubExchangeResponse;
import com.example.demo.auth.dto.PasswordLoginRequest;
import com.example.demo.auth.dto.PasswordResetRequest;
import com.example.demo.auth.dto.PasswordResetSendRequest;
import com.example.demo.auth.dto.RefreshTokenRequest;
import com.example.demo.auth.dto.RegisterRequest;
import com.example.demo.auth.application.AuthService;
import com.example.demo.auth.application.GithubOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/**
 * 认证模块对外 REST 接口。提供注册、密码 / 邮箱验证码登录、刷新令牌、
 * 修改密码、人脸二次验证以及 GitHub OAuth 登录等端点。
 */
public class AuthController {

    private final AuthService authService;
    private final GithubOAuthService githubOAuthService;

    @PostMapping("/register")
    /**
     * 用户注册。校验用户名/邮箱唯一性后写入用户记录并发送邮箱确认验证码。
     */
    public ApiResponse<EmailCodeSendResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @GetMapping("/has-users")
    /**
     * 判断系统是否已存在任意用户，用于初始化阶段是否需要跳转到注册页。
     */
    public ApiResponse<Boolean> hasUsers() {
        return ApiResponse.success(authService.hasUsers());
    }

    @PostMapping("/login/password")
    /**
     * 用户名 / 邮箱 + 密码登录。校验成功后签发 access/refresh token；若用户开启人脸二次验证则返回挑战。
     */
    public ApiResponse<AuthTokenResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.success(authService.loginByPassword(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/login/email/send-code")
    /**
     * 发送邮箱登录验证码。返回下一次可重新发送的冷却秒数。
     */
    public ApiResponse<EmailCodeSendResponse> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        int cooldown = authService.sendLoginCode(request.getEmail());
        return ApiResponse.success(EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("验证码已发送，请注意查收")
                .build());
    }

    @PostMapping("/password/reset/send-code")
    /**
     * 发送重置密码验证码。为防止账号探测，对已注册与未注册邮箱返回相同文案。
     */
    public ApiResponse<EmailCodeSendResponse> sendResetPasswordCode(@Valid @RequestBody PasswordResetSendRequest request) {
        int cooldown = authService.sendResetPasswordCode(request.getEmail());
        return ApiResponse.success(EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("如果该邮箱已注册，验证码将发送到邮箱")
                .build());
    }

    @PostMapping("/password/reset")
    /**
     * 使用邮箱验证码重置密码，成功后会自增 tokenVersion 使所有旧令牌失效。
     */
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ApiResponse.success(null, "密码重置成功，请使用新密码登录");
    }

    @PostMapping("/login/email")
    /**
     * 邮箱验证码登录，同时用于完成首次邮箱验证并签发令牌。
     */
    public ApiResponse<AuthTokenResponse> loginByEmailCode(@Valid @RequestBody EmailCodeLoginRequest request) {
        return ApiResponse.success(authService.loginByEmailCode(request.getEmail(), request.getCode()));
    }

    @PostMapping("/token/refresh")
    /**
     * 使用 refreshToken 换取新的 access/refresh token 对。
     */
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    @GetMapping("/me")
    /**
     * 获取当前登录用户的资料信息。
     */
    public ApiResponse<AuthUserProfile> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        Long userId = authService.extractUserIdFromJwt(jwt);
        return ApiResponse.success(authService.getProfile(userId));
    }

    @PutMapping("/password")
    /**
     * 修改当前用户密码。校验旧密码后写入新密码并自增 tokenVersion 使其他设备下线。
     */
    public ApiResponse<Void> changePassword(JwtAuthenticationToken authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ApiResponse.success(null, "密码修改成功");
    }

    @PostMapping("/logout")
    /**
     * 用户登出，通过自增 tokenVersion 让该用户所有现存令牌失效。
     */
    public ApiResponse<Void> logout(JwtAuthenticationToken authentication) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        authService.logout(userId);
        return ApiResponse.success(null, "退出成功");
    }

    @GetMapping("/oauth/github/authorize")
    /**
     * 生成 GitHub OAuth 授权地址，并签发一次性 state 用于回调校验。
     */
    public ApiResponse<GithubAuthorizeResponse> githubAuthorize(@RequestParam(required = false) String redirect) {
        return ApiResponse.success(githubOAuthService.createAuthorizeUrl(redirect));
    }

    @PostMapping("/oauth/github/exchange")
    /**
     * 用 GitHub 回调的 code 换取系统令牌。首次登录会自动创建本地账号并绑定 OAuth 关系。
     */
    public ApiResponse<GithubExchangeResponse> githubExchange(@Valid @RequestBody GithubExchangeRequest request) {
        return ApiResponse.success(githubOAuthService.exchangeCode(request.getCode(), request.getState()));
    }

    @GetMapping("/face/status")
    /**
     * 查询当前用户人脸绑定及二次验证开关状态。
     */
    public ApiResponse<FaceStatusResponse> faceStatus(JwtAuthenticationToken authentication) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        return ApiResponse.success(authService.getFaceStatus(userId));
    }

    @PostMapping("/face/register")
    /**
     * 绑定或重新绑定当前用户的人脸特征。会与历史模板合并保留高质量样本。
     */
    public ApiResponse<FaceStatusResponse> registerFace(JwtAuthenticationToken authentication,
                                                        @Valid @RequestBody FaceRegisterRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        return ApiResponse.success(authService.registerFace(userId, request.getImageBase64()), "人脸信息已绑定");
    }

    @PutMapping("/face/required")
    /**
     * 开启或关闭当前用户的人脸二次验证；开启前必须已经成功绑定人脸。
     */
    public ApiResponse<FaceStatusResponse> toggleFaceRequired(JwtAuthenticationToken authentication,
                                                              @Valid @RequestBody FaceRequiredRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        boolean required = Boolean.TRUE.equals(request.getRequired());
        return ApiResponse.success(authService.updateFaceRequired(userId, required),
                required ? "已开启人脸二次验证" : "已关闭人脸二次验证");
    }

    @PostMapping("/face/verify-login")
    /**
     * 使用预认证令牌 + 人脸图片完成第二步登录，签发最终令牌。
     */
    public ApiResponse<AuthTokenResponse> verifyFaceLogin(@Valid @RequestBody FaceLoginVerifyRequest request) {
        return ApiResponse.success(authService.verifyFaceLogin(request.getPreAuthToken(), request.getImageBase64()));
    }
}
