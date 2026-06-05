package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class XhsCrawlResponse {

    private String datasetId;

    private Integer targetCount;

    private Integer count;

    private List<String> usedKeywords;

    private List<XhsNoteItemView> items;

    private String status;

    private String message;

    private LocalDateTime createdAt;
}
