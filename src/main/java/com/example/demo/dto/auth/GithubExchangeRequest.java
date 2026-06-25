package com.example.demo.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubExchangeRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;
}
