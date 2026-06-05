package com.dora.jagent.xhs.model;

import lombok.Data;

@Data
public class XhsGenerateRequest {

    private String datasetId;

    private String modelKey = "deepseek-chat";

    private String audience = "";

    private String tone = "真实分享";

    private Integer topicCount = 3;

    private Integer contentCountPerTopic = 2;

    private boolean generateImages = true;
}
