package com.dora.jagent.xhs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsContentItem {

    private String title;

    private String body;

    private List<String> hashtags;

    private String cta;

    private String imageSuggestion;

    private String contentType;
}
