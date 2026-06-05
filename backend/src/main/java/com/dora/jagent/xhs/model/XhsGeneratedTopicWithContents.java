package com.dora.jagent.xhs.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class XhsGeneratedTopicWithContents {

    private XhsTopicItem topic;

    private List<XhsContentItem> contents;
}
