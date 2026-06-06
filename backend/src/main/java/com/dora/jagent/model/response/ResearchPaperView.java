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
public class ResearchPaperView {

    private String id;

    private String openalexId;

    private String doi;

    private String title;

    private String abstractText;

    private Integer publicationYear;

    private String sourceName;

    private String sourceType;

    private String authors;

    private String landingPageUrl;

    private String pdfUrl;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
