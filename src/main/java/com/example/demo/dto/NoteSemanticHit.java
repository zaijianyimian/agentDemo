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
public class NoteSemanticHit {

    private Long noteId;

    private String title;

    private String contentSnippet;

    private String tags;

    private String aiSummary;

    private Double score;

    private LocalDateTime updateTime;
}
