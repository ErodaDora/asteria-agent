package com.dora.jagent.repository;

import com.dora.jagent.model.entity.ResearchPaper;
import com.dora.jagent.model.entity.ResearchPaperChunkBgeM3;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public interface ResearchPaperChunkBgeM3Repository {

    ResearchPaperChunkBgeM3 save(ResearchPaperChunkBgeM3 chunk);

    List<ResearchPaperChunkMatch> similaritySearch(String collectionName, String vectorLiteral, int limit);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ResearchPaperChunkMatch {
        private ResearchPaperChunkBgeM3 chunk;
        private ResearchPaper paper;
        private Double distance;
    }
}
