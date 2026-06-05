package com.dora.jagent.xhs.service;

import com.dora.jagent.xhs.model.XhsContentItem;
import com.dora.jagent.xhs.model.XhsTopicItem;

import java.util.List;

public interface XhsContentGenerationService {

    List<XhsContentItem> generateContents(String modelKey, XhsTopicItem topic, String audience, String tone, int count);
}
