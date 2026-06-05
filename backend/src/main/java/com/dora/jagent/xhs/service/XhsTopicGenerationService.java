package com.dora.jagent.xhs.service;

import com.dora.jagent.xhs.model.XhsAnalysisResult;
import com.dora.jagent.xhs.model.XhsTopicItem;

import java.util.List;

public interface XhsTopicGenerationService {

    List<XhsTopicItem> generateTopics(String modelKey, XhsAnalysisResult analysisResult, String audience, int count);
}
