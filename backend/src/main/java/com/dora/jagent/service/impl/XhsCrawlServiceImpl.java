package com.dora.jagent.service.impl;

import com.dora.jagent.model.request.XhsCrawlRequest;
import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.model.response.XhsLoginStatusResponse;
import com.dora.jagent.service.XhsBrowserSessionService;
import com.dora.jagent.service.XhsCrawlService;
import com.dora.jagent.service.XhsPlaywrightCrawlerService;
import com.dora.jagent.service.XhsStorageService;
import com.dora.jagent.service.XhsTextProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XhsCrawlServiceImpl implements XhsCrawlService {

    private final XhsBrowserSessionService xhsBrowserSessionService;
    private final XhsPlaywrightCrawlerService xhsPlaywrightCrawlerService;
    private final XhsTextProcessingService xhsTextProcessingService;
    private final XhsStorageService xhsStorageService;

    @Override
    public XhsCrawlResponse crawl(String userId, XhsCrawlRequest request) {
        List<String> keywords = request.getKeywords() == null ? List.of() : request.getKeywords().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        if (keywords.isEmpty()) {
            return XhsCrawlResponse.builder()
                    .datasetId(UUID.randomUUID().toString())
                    .targetCount(request.getTargetCount())
                    .count(0)
                    .usedKeywords(List.of())
                    .items(List.of())
                    .status("invalid_request")
                    .message("请至少输入一个关键词")
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        if (!xhsBrowserSessionService.hasStoredLoginState()) {
            XhsLoginStatusResponse status = xhsBrowserSessionService.startLoginSession();
            XhsCrawlResponse response = XhsCrawlResponse.builder()
                    .datasetId(UUID.randomUUID().toString())
                    .targetCount(request.getTargetCount())
                    .count(0)
                    .usedKeywords(keywords)
                    .items(List.of())
                    .status("login_required")
                    .message("当前未检测到登录态，已自动打开登录页面。请登录完成后再次点击开始检索。"
                            + (StringUtils.hasText(status.getMessage()) ? " " + status.getMessage() : ""))
                    .createdAt(LocalDateTime.now())
                    .build();
            return xhsStorageService.saveLatest(userId, response);
        }

        List<XhsNoteItemView> rawItems = xhsPlaywrightCrawlerService.crawl(request);
        List<XhsNoteItemView> normalizedItems = xhsTextProcessingService.normalize(rawItems);

        XhsCrawlResponse response = XhsCrawlResponse.builder()
                .datasetId(UUID.randomUUID().toString())
                .targetCount(request.getTargetCount())
                .count(normalizedItems.size())
                .usedKeywords(keywords)
                .items(normalizedItems)
                .status("success")
                .message(normalizedItems.isEmpty() ? "采集完成，但当前条件下没有筛出符合要求的笔记" : "采集完成")
                .createdAt(LocalDateTime.now())
                .build();

        return xhsStorageService.saveLatest(userId, response);
    }

    @Override
    public XhsCrawlResponse getLatest(String userId) {
        return xhsStorageService.getLatest(userId);
    }
}
