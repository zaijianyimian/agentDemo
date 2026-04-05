package com.example.demo.dto.auth;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class FaceRequiredRequest {
    @NotNull(message = "required 不能为空")
    private Boolean required;
}
