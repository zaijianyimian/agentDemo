package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GithubExchangeResponse {
    private AuthTokenResponse token;
    private String redirectPath;
}
