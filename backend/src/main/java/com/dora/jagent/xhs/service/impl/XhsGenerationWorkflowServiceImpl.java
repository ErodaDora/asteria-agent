package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.service.NotionSyncService;
import com.dora.jagent.service.XhsStorageService;
import com.dora.jagent.xhs.model.XhsAnalysisResult;
import com.dora.jagent.xhs.model.XhsContentItem;
import com.dora.jagent.xhs.model.XhsGenerateRequest;
import com.dora.jagent.xhs.model.XhsGenerateResponse;
import com.dora.jagent.xhs.model.XhsGeneratedTopicWithContents;
import com.dora.jagent.xhs.model.XhsTopicItem;
import com.dora.jagent.xhs.service.XhsAnalysisService;
import com.dora.jagent.xhs.service.XhsContentGenerationService;
import com.dora.jagent.xhs.service.XhsGenerationWorkflowService;
import com.dora.jagent.xhs.service.XhsImageGenerationService;
import com.dora.jagent.xhs.service.XhsTopicGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class XhsGenerationWorkflowServiceImpl implements XhsGenerationWorkflowService {

    private final XhsStorageService xhsStorageService;
    private final NotionSyncService notionSyncService;
    private final XhsAnalysisService xhsAnalysisService;
    private final XhsTopicGenerationService xhsTopicGenerationService;
    private final XhsContentGenerationService xhsContentGenerationService;
    private final XhsImageGenerationService xhsImageGenerationService;
    private final Map<String, XhsGenerateResponse> latestResults = new ConcurrentHashMap<>();

    @Value("${llm.model}")
    private String defaultModelKey;

    @Override
    public XhsGenerateResponse generate(String userId, XhsGenerateRequest request) {
        XhsCrawlResponse latestCrawl = xhsStorageService.getLatest(userId);
        if (latestCrawl == null || latestCrawl.getItems() == null || latestCrawl.getItems().isEmpty()) {
            throw new BizException("当前没有可用于生成的采集结果，请先完成小红书采集");
        }
        if (StringUtils.hasText(request.getDatasetId()) && !request.getDatasetId().equals(latestCrawl.getDatasetId())) {
            throw new BizException("datasetId 与最近一次采集结果不一致");
        }

        String modelKey = StringUtils.hasText(request.getModelKey()) ? request.getModelKey().trim() : defaultModelKey;
        XhsAnalysisResult analysisResult = xhsAnalysisService.analyze(latestCrawl.getItems());
        List<XhsTopicItem> topics = xhsTopicGenerationService.generateTopics(
                modelKey,
                analysisResult,
                request.getAudience(),
                safePositive(request.getTopicCount(), 3)
        );

        List<XhsGeneratedTopicWithContents> results = new ArrayList<>();
        for (XhsTopicItem topic : topics) {
            List<XhsContentItem> contents = xhsContentGenerationService.generateContents(
                    modelKey,
                    topic,
                    request.getAudience(),
                    request.getTone(),
                    safePositive(request.getContentCountPerTopic(), 2)
            );
            results.add(XhsGeneratedTopicWithContents.builder()
                    .topic(topic)
                    .contents(contents)
                    .build());
        }

        XhsGenerateResponse response = XhsGenerateResponse.builder()
                .datasetId(latestCrawl.getDatasetId())
                .modelKey(modelKey)
                .imageGenerationStatus(xhsImageGenerationService.resolveImageGenerationStatus(modelKey, request.isGenerateImages()))
                .analysisSummary(analysisResult.getSummary())
                .topKeywords(analysisResult.getTopKeywords())
                .topTags(analysisResult.getTopTags())
                .titlePatterns(analysisResult.getTitlePatterns())
                .insightPoints(analysisResult.getInsightPoints())
                .results(results)
                .createdAt(LocalDateTime.now())
                .build();

        latestResults.put(userId, response);
        return response;
    }

    @Override
    public XhsGenerateResponse getLatest(String userId) {
        return latestResults.get(userId);
    }

    @Override
    public XhsNotionSyncResponse syncLatestToNotion(String userId, String datasetId) {
        XhsGenerateResponse generated = latestResults.get(userId);
        if (generated == null) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("当前没有可同步的生成结果")
                    .build();
        }
        if (StringUtils.hasText(datasetId) && !datasetId.equals(generated.getDatasetId())) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("datasetId 与当前生成结果不一致")
                    .build();
        }
        XhsCrawlResponse latestCrawl = xhsStorageService.getLatest(userId);
        return notionSyncService.syncGeneratedResult(generated, latestCrawl);
    }

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
