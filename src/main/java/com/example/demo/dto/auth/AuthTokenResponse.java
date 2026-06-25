package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthTokenResponse {
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private AuthUserProfile user;
    private boolean requiresSecondFactor;
    private String preAuthToken;
    private long preAuthExpiresIn;
}
