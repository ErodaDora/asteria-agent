package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class XhsPublishResponse {

    private boolean success;

    private String publishMode;

    private boolean loginRequired;

    private boolean manualActionRequired;

    private String message;

    private String publishedTitle;

    private String coverImageUrl;

    private String localImagePath;
}
