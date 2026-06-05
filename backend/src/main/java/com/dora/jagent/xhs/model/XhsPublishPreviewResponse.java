package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class XhsPublishPreviewResponse {

    private String datasetId;

    private Integer topicIndex;

    private Integer contentIndex;

    private String title;

    private String body;

    private String cta;

    private List<String> hashtags;

    private String coverImageUrl;

    private String publishMode;

    private boolean isOriginal;

    private String visibility;
}
