package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchPaperCollectionView {

    private String id;

    private String paperId;

    private String collectionName;

    private String note;

    private String status;

    private ResearchPaperView paper;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
