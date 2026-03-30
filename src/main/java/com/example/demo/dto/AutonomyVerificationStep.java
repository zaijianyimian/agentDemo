package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自治验证步骤结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyVerificationStep {

    private String name;

    private boolean success;

    private Integer exitCode;

    private String workingDirectory;

    private String output;
}
