package com.dora.jagent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchPaperChunkBgeM3 {

    private String id;

    private String paperId;

    private String chunkType;

    private Integer chunkIndex;

    private String content;

    private String embeddingText;

    private String metadata;

    private float[] embedding;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
