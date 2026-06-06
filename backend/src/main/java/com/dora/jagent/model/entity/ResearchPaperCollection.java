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
public class ResearchPaperCollection {

    private String id;

    private String paperId;

    private String collectionName;

    private String note;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
