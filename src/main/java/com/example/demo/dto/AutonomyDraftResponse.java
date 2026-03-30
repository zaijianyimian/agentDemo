package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 自治补全草稿结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyDraftResponse {

    private LocalDateTime generateTime;

    private String target;

    private String draftPath;

    private String content;

    private String policyNote;
}
