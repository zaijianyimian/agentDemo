package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GithubAuthorizeResponse {
    private String authorizationUrl;
    private int stateExpiresIn;
}
