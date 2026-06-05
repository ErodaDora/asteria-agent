package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class XhsGenerateResponse {

    private String datasetId;

    private String modelKey;

    private String imageGenerationStatus;

    private String analysisSummary;

    private List<String> topKeywords;

    private List<String> topTags;

    private List<String> titlePatterns;

    private List<String> insightPoints;

    private List<XhsGeneratedTopicWithContents> results;

    private LocalDateTime createdAt;
}
