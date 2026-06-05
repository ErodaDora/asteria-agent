package com.dora.jagent.model.request;

import lombok.Data;

import java.util.List;

@Data
public class XhsCrawlRequest {

    private List<String> keywords;

    private List<String> topicWords;

    private Integer minComments = 0;

    private Integer minLikes = 0;

    private Integer minFavorites = 0;

    private Integer targetCount = 20;
}
