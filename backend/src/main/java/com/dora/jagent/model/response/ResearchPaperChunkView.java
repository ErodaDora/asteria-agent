package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchPaperChunkView {

    private String id;

    private String paperId;

    private String chunkType;

    private Integer chunkIndex;

    private String content;

    private String metadata;

    private Double distance;

    private ResearchPaperView paper;
}
