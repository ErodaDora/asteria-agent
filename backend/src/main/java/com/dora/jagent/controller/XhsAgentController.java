package com.dora.jagent.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.model.request.XhsCrawlRequest;
import com.dora.jagent.model.request.XhsNotionSyncRequest;
import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsLoginStatusResponse;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.service.XhsBrowserSessionService;
import com.dora.jagent.service.XhsCrawlService;
import com.dora.jagent.service.XhsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/xhs")
@RequiredArgsConstructor
public class XhsAgentController {

    private final XhsBrowserSessionService xhsBrowserSessionService;
    private final XhsCrawlService xhsCrawlService;
    private final XhsStorageService xhsStorageService;

    @PostMapping("/browser/login/start")
    public ApiResponse<XhsLoginStatusResponse> startLoginWindow(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsBrowserSessionService.startLoginSession());
    }

    @GetMapping("/browser/login/status")
    public ApiResponse<XhsLoginStatusResponse> loginStatus(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsBrowserSessionService.getLoginStatus());
    }

    @PostMapping("/crawl/search")
    public ApiResponse<XhsCrawlResponse> crawl(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsCrawlRequest request
    ) {
        return ApiResponse.success(xhsCrawlService.crawl(currentUserId, request));
    }

    @GetMapping("/crawl/latest")
    public ApiResponse<XhsCrawlResponse> latest(
            @RequestAttribute("currentUserId") String currentUserId
    ) {
        return ApiResponse.success(xhsCrawlService.getLatest(currentUserId));
    }

    @PostMapping("/storage/notion/sync")
    public ApiResponse<XhsNotionSyncResponse> syncToNotion(
            @RequestAttribute("currentUserId") String currentUserId,
            @RequestBody XhsNotionSyncRequest request
    ) {
        return ApiResponse.success(xhsStorageService.syncLatestToNotion(currentUserId, request.getDatasetId()));
    }
}
