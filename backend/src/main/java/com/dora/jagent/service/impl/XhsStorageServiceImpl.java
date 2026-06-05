package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.service.NotionSyncService;
import com.dora.jagent.service.XhsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class XhsStorageServiceImpl implements XhsStorageService {

    private final Map<String, XhsCrawlResponse> latestResults = new ConcurrentHashMap<>();
    private final NotionSyncService notionSyncService;

    @Override
    public XhsCrawlResponse saveLatest(String userId, XhsCrawlResponse response) {
        latestResults.put(userId, response);
        return response;
    }

    @Override
    public XhsCrawlResponse getLatest(String userId) {
        return latestResults.get(userId);
    }

    @Override
    public XhsNotionSyncResponse syncLatestToNotion(String userId, String datasetId) {
        XhsCrawlResponse latest = latestResults.get(userId);
        if (latest == null) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("当前没有可同步的采集结果")
                    .build();
        }
        if (StringUtils.hasText(datasetId) && !datasetId.equals(latest.getDatasetId())) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("datasetId 与当前暂存结果不一致")
                    .build();
        }
        return notionSyncService.syncCrawlResult(latest);
    }
}
