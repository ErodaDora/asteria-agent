package com.dora.jagent.xhs.model;

import lombok.Data;

import java.util.List;

@Data
public class XhsPublishRequest {

    private String datasetId;

    private Integer topicIndex = 0;

    private Integer contentIndex = 0;

    private String mode = "mcp";

    private Boolean isOriginal = true;

    private String visibility = "公开可见";

    private String scheduleAt;

    private List<String> products;
}
