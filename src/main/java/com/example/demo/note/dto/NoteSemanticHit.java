package com.example.demo.note.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记语义检索命中结果 DTO。
 * <p>
 * 封装向量库语义搜索返回的单条命中记录,包含命中的笔记 ID、标题、内容片段、标签、AI 摘要、相似度得分以及更新时间。
 * 由 NoteVectorService 在 Qdrant 检索后组装,并通过 NoteController 暴露给前端展示。
 */
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
