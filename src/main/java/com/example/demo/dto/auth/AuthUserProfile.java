package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthUserProfile {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private Boolean emailVerified;
}
