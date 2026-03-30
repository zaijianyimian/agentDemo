package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.auth.AuthTokenResponse;
import com.example.demo.dto.auth.AuthUserProfile;
import com.example.demo.dto.auth.ChangePasswordRequest;
import com.example.demo.dto.auth.EmailCodeLoginRequest;
import com.example.demo.dto.auth.EmailCodeSendRequest;
import com.example.demo.dto.auth.EmailCodeSendResponse;
import com.example.demo.dto.auth.GithubAuthorizeResponse;
import com.example.demo.dto.auth.GithubExchangeRequest;
import com.example.demo.dto.auth.GithubExchangeResponse;
import com.example.demo.dto.auth.PasswordLoginRequest;
import com.example.demo.dto.auth.RefreshTokenRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.service.auth.AuthService;
import com.example.demo.service.auth.GithubOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GithubOAuthService githubOAuthService;

    @PostMapping("/register")
    public ApiResponse<EmailCodeSendResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login/password")
    public ApiResponse<AuthTokenResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.success(authService.loginByPassword(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/login/email/send-code")
    public ApiResponse<EmailCodeSendResponse> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        int cooldown = authService.sendLoginCode(request.getEmail());
        return ApiResponse.success(EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("验证码已发送，请注意查收")
                .build());
    }

    @PostMapping("/login/email")
    public ApiResponse<AuthTokenResponse> loginByEmailCode(@Valid @RequestBody EmailCodeLoginRequest request) {
        return ApiResponse.success(authService.loginByEmailCode(request.getEmail(), request.getCode()));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserProfile> me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        Long userId = authService.extractUserIdFromJwt(jwt);
        return ApiResponse.success(authService.getProfile(userId));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(JwtAuthenticationToken authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ApiResponse.success(null, "密码修改成功");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(JwtAuthenticationToken authentication) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        authService.logout(userId);
        return ApiResponse.success(null, "退出成功");
    }

    @GetMapping("/oauth/github/authorize")
    public ApiResponse<GithubAuthorizeResponse> githubAuthorize(@RequestParam(required = false) String redirect) {
        return ApiResponse.success(githubOAuthService.createAuthorizeUrl(redirect));
    }

    @PostMapping("/oauth/github/exchange")
    public ApiResponse<GithubExchangeResponse> githubExchange(@Valid @RequestBody GithubExchangeRequest request) {
        return ApiResponse.success(githubOAuthService.exchangeCode(request.getCode(), request.getState()));
    }
}
