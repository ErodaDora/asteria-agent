package com.dora.jagent.xhs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class XhsMcpPublishPayload {

    @JsonProperty("Title")
    private String Title;

    @JsonProperty("Content")
    private String Content;

    @JsonProperty("Images")
    private List<String> Images;

    @JsonProperty("Tags")
    private List<String> Tags;

    @JsonProperty("IsOriginal")
    private boolean IsOriginal;

    @JsonProperty("Visibility")
    private String Visibility;
}
