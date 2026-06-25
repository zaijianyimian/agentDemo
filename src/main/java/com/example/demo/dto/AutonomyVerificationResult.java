package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自治验证结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyVerificationResult {

    private LocalDateTime verifyTime;

    private boolean success;

    private List<AutonomyVerificationStep> steps;
}
