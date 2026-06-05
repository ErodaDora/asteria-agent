package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class XhsAnalysisResult {

    private int totalCount;

    private List<String> topKeywords;

    private List<String> topTags;

    private List<String> titlePatterns;

    private List<String> insightPoints;

    private String summary;
}
