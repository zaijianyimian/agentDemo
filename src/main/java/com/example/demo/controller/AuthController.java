package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.auth.AuthTokenResponse;
import com.example.demo.dto.auth.AuthUserProfile;
import com.example.demo.dto.auth.ChangePasswordRequest;
import com.example.demo.dto.auth.EmailCodeLoginRequest;
import com.example.demo.dto.auth.EmailCodeSendRequest;
import com.example.demo.dto.auth.EmailCodeSendResponse;
import com.example.demo.dto.auth.FaceLoginVerifyRequest;
import com.example.demo.dto.auth.FaceRegisterRequest;
import com.example.demo.dto.auth.FaceRequiredRequest;
import com.example.demo.dto.auth.FaceStatusResponse;
import com.example.demo.dto.auth.GithubAuthorizeResponse;
import com.example.demo.dto.auth.GithubExchangeRequest;
import com.example.demo.dto.auth.GithubExchangeResponse;
import com.example.demo.dto.auth.PasswordLoginRequest;
import com.example.demo.dto.auth.PuzzleCaptchaResponse;
import com.example.demo.dto.auth.PuzzleCaptchaVerifyRequest;
import com.example.demo.dto.auth.PuzzleCaptchaVerifyResponse;
import com.example.demo.dto.auth.RefreshTokenRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.service.auth.AuthService;
import com.example.demo.service.auth.GithubOAuthService;
import com.example.demo.service.auth.PuzzleCaptchaService;
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
    private final PuzzleCaptchaService puzzleCaptchaService;

    @GetMapping("/captcha/puzzle")
    public ApiResponse<PuzzleCaptchaResponse> getPuzzleCaptcha() {
        return ApiResponse.success(puzzleCaptchaService.createPuzzleCaptcha());
    }

    @PostMapping("/captcha/puzzle/verify")
    public ApiResponse<PuzzleCaptchaVerifyResponse> verifyPuzzleCaptcha(@Valid @RequestBody PuzzleCaptchaVerifyRequest request) {
        return ApiResponse.success(puzzleCaptchaService.verifyPuzzleCaptcha(request.getCaptchaId(), request.getSliderPercent()));
    }

    @PostMapping("/register")
    public ApiResponse<EmailCodeSendResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login/password")
    public ApiResponse<AuthTokenResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.success(authService.loginByPassword(request.getUsername(), request.getPassword(), request.getCaptchaTicket()));
    }

    @PostMapping("/login/email/send-code")
    public ApiResponse<EmailCodeSendResponse> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        int cooldown = authService.sendLoginCode(request.getEmail(), request.getCaptchaTicket());
        return ApiResponse.success(EmailCodeSendResponse.builder()
                .cooldownSeconds(cooldown)
                .message("验证码已发送，请注意查收")
                .build());
    }

    @PostMapping("/login/email")
    public ApiResponse<AuthTokenResponse> loginByEmailCode(@Valid @RequestBody EmailCodeLoginRequest request) {
        return ApiResponse.success(authService.loginByEmailCode(request.getEmail(), request.getCode(), request.getCaptchaTicket()));
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

    @GetMapping("/face/status")
    public ApiResponse<FaceStatusResponse> faceStatus(JwtAuthenticationToken authentication) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        return ApiResponse.success(authService.getFaceStatus(userId));
    }

    @PostMapping("/face/register")
    public ApiResponse<FaceStatusResponse> registerFace(JwtAuthenticationToken authentication,
                                                        @Valid @RequestBody FaceRegisterRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        return ApiResponse.success(authService.registerFace(userId, request.getImageBase64()), "人脸信息已绑定");
    }

    @PutMapping("/face/required")
    public ApiResponse<FaceStatusResponse> toggleFaceRequired(JwtAuthenticationToken authentication,
                                                              @RequestBody FaceRequiredRequest request) {
        Long userId = authService.extractUserIdFromJwt(authentication.getToken());
        boolean required = Boolean.TRUE.equals(request.getRequired());
        return ApiResponse.success(authService.updateFaceRequired(userId, required),
                required ? "已开启人脸二次验证" : "已关闭人脸二次验证");
    }

    @PostMapping("/face/verify-login")
    public ApiResponse<AuthTokenResponse> verifyFaceLogin(@Valid @RequestBody FaceLoginVerifyRequest request) {
        return ApiResponse.success(authService.verifyFaceLogin(request.getPreAuthToken(), request.getImageBase64()));
    }
}
