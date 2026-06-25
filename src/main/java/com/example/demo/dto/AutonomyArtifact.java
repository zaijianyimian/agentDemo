package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyArtifact {

    private String type;

    private String name;

    private String path;

    private LocalDateTime time;

    private String preview;
}
