package com.example.demo.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaceStatusResponse {
    private boolean enrolled;
    private boolean required;
    private boolean enabled;
    private Integer vectorDimension;
    private Double qualityScore;
}
